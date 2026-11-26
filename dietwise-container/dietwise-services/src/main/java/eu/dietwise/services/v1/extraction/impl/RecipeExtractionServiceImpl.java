package eu.dietwise.services.v1.extraction.impl;

import static eu.dietwise.common.utils.StringUtils.limit;
import static eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter.segment;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderRequest;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.filtering.MarkdownBlockCoalescer;
import eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter;
import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ApplicationScoped
public class RecipeExtractionServiceImpl implements RecipeExtractionService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeExtractionServiceImpl.class);

	private final RecipeFilterAiFacade filterAiFacade;
	private final MarkdownRecipeExtractionService extractionService;
	private final RendererClient rendererClient;
	private final boolean allowCachedTestPages;
	private final ObjectMapper objectMapper;
	private final RecipeJsonCoercionStrategy recipeJsonCoercionStrategy;

	public RecipeExtractionServiceImpl(
			RecipeFilterAiFacade filterAiFacade,
			MarkdownRecipeExtractionService extractionService,
			@ConfigProperty(name = "dietwise.renderer.allow-cached-test-pages", defaultValue = "false") boolean allowCachedTestPages,
			@RestClient RendererClient rendererClient,
			ObjectMapper objectMapper,
			RecipeJsonCoercionStrategy recipeJsonCoercionStrategy
	) {
		this.filterAiFacade = filterAiFacade;
		this.extractionService = extractionService;
		this.allowCachedTestPages = allowCachedTestPages;
		this.rendererClient = rendererClient;
		this.objectMapper = objectMapper;
		this.recipeJsonCoercionStrategy = recipeJsonCoercionStrategy;
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipeFromJsonLdOrMarkdown(UUID correlationId, RecipeAssessmentParam param) {
		return parseJsonLdContent(correlationId, param)
				.flatMap(jsonLdRecipes -> useJsonLdRecipesOrAi(correlationId, param.getUrl(), param.getLang(), jsonLdRecipes, param.getPageContent()));
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> useAiToExtractRecipeFromMarkdown(UUID correlationId, String url, RecipeLanguage lang, String markdown) {
		return keepOnlyRelevantPageContent(markdown, lang)
				.map(filteredContent -> ImmutableRecipeAssessmentParam.builder().url(url).lang(lang).pageContent(filteredContent).build())
				.flatMap(this::extractRecipeFromMarkdown)
				.flatMap(recipe -> failIfRecipeHasNoIngredients(correlationId, url, recipe))
				.map(llmResponse -> convertLlmResponseToRecipes(llmResponse, markdown));
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipeFromUrl(UUID correlationId, RecipeExtractionAndAssessmentParam param) {
		RecipeSourceUrlValidator.validateHttpPublicUrl(param.getUrl(), allowCachedTestPages);
		var renderRequest = new RenderRequest(param.getUrl(), true, true, 30000, param.getViewport().orElse(null), false, true);
		return rendererClient.render(renderRequest)
				.map(RestResponse::getEntity)
				.flatMap(renderResponse -> useJsonLdRecipesOrAi(
						correlationId,
						param.getUrl(),
						param.getLang(),
						Objects.requireNonNullElse(renderResponse.jsonLdRecipes(), List.of()),
						renderResponse.output()
				));
	}

	private Uni<RecipeExtractionRecipeAssessmentMessage> useJsonLdRecipesOrAi(
			UUID correlationId, String url, RecipeLanguage lang, List<RecipeExtractedFromInput> jsonLdRecipes, String markdown) {
		List<RecipeExtractedFromInput> sanitizedJsonLdRecipes = sanitizeRecipes(correlationId, url, jsonLdRecipes);
		if (sanitizedJsonLdRecipes.isEmpty()) {
			LOG.info("No usable JSON-LD recipes, will use AI <{}> for {}", correlationId, url);
			LOG.debug("Page content: (length {}): {}", markdown.length(), limit(markdown, 1000));
			return useAiToExtractRecipeFromMarkdown(correlationId, url, lang, markdown);
		}
		LOG.info("Found JSON-LD format recipes <{}> in {} ({} in total)", correlationId, url, sanitizedJsonLdRecipes.size());
		LOG.debug("JSON-LD recipes: {}", sanitizedJsonLdRecipes);
		var recipes = sanitizedJsonLdRecipes.stream()
				.map(this::toRecipeWithOnlyIngredientNames)
				.map(r -> new RecipeAndDetectionType(r, JSONLD))
				.toList();
		return Uni.createFrom().item(new RecipeExtractionRecipeAssessmentMessage(recipes, markdown));
	}

	private Uni<String> keepOnlyRelevantPageContent(String pageTextAsMarkdown, RecipeLanguage lang) {
		List<MarkdownBlockSegmenter.Block> segmentedContent = segment(pageTextAsMarkdown);
		List<String> blocks = MarkdownBlockCoalescer.coalesce(segmentedContent, 5000);
		return Multi.createFrom().iterable(blocks).onItem().transformToUniAndConcatenate(block -> filterRecipeBlock(lang, block))
				.filter(block -> !block.isBlank())
				.collect().asList()
				.map(filteredBlocks -> String.join("\n\n", filteredBlocks));
	}

	private Uni<String> filterRecipeBlock(RecipeLanguage lang, String block) {
		return filterAiFacade.filterRecipeBlock(lang, block)
				.map(result -> result == null ? "" : result.trim().toUpperCase()) // sanitize
				.map(result -> "KEEP".equals(result) ? block : "");
	}

	private Uni<Recipe> extractRecipeFromMarkdown(RecipeAssessmentParam param) {
		return extractionService.extractRecipeFromMarkdown(param.getLang(), param.getPageContent())
				.map(this::toRecipeWithOnlyIngredientNames);
	}

	private Uni<List<RecipeExtractedFromInput>> parseJsonLdContent(UUID correlationId, RecipeAssessmentParam param) {
		String jsonLdContent = param.getJsonLdContent();
		if (jsonLdContent == null || jsonLdContent.isBlank()) {
			return Uni.createFrom().item(List.of());
		}
		LOG.info("Will try JSON-LD content first <{}> for {}", correlationId, param.getUrl());
		return Uni.createFrom().item(() -> {
			try {
				return parseJsonLdRecipes(jsonLdContent);
			} catch (JacksonException e) {
				LOG.warn("JSON-LD content was not usable, will fall back to page content <{}> for {}", correlationId, param.getUrl(), e);
				return List.of();
			}
		});
	}

	private Recipe toRecipeWithOnlyIngredientNames(RecipeExtractedFromInput inputRecipe) {
		return ImmutableRecipe.builder()
				.name(inputRecipe.name())
				.recipeYield(inputRecipe.recipeYield())
				.recipeIngredients(distinctIngredientNames(inputRecipe.recipeIngredients()).stream().map(this::fromName).toList())
				.recipeInstructions(inputRecipe.recipeInstructions())
				.text(inputRecipe.text())
				.build();
	}

	/**
	 * Recipes often repeat an ingredient, e.g. "salt" for the dough and for the sauce; keep the first of the names that
	 * differ only in case or surrounding whitespace. Accents are significant, they distinguish words, e.g. the Greek
	 * "πιπεριά" and "πιπέρια".
	 */
	private List<String> distinctIngredientNames(List<String> names) {
		Set<String> seen = new HashSet<>();
		return names.stream().filter(name -> seen.add(name.strip().toLowerCase(Locale.ROOT))).toList();
	}

	private Ingredient fromName(String name) {
		return ImmutableIngredient.builder().id(new GenericIngredientId(UUID.randomUUID().toString())).nameInRecipe(name).build();
	}

	private Uni<Recipe> failIfRecipeHasNoIngredients(UUID correlationId, String url, Recipe recipe) {
		List<Ingredient> filteredIngredients = recipe.getRecipeIngredients().stream().filter(Ingredient::hasName).toList();
		if (filteredIngredients.isEmpty()) {
			LOG.warn("AI extracted recipe without ingredients <{}> in {}", correlationId, url);
			return Uni.createFrom().failure(new NoIngredientsInRecipeException());
		}
		var recipeToReturn = filteredIngredients.size() == recipe.getRecipeIngredients().size()
				? recipe
				: ImmutableRecipe.copyOf(recipe).withRecipeIngredients(filteredIngredients);
		return Uni.createFrom().item(recipeToReturn);
	}

	private RecipeExtractionRecipeAssessmentMessage convertLlmResponseToRecipes(Recipe recipe, String pageText) {
		return new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, LLM_FROM_TEXT)), pageText);
	}

	private List<RecipeExtractedFromInput> sanitizeRecipes(UUID correlationId, String url, List<RecipeExtractedFromInput> recipes) {
		return recipes.stream()
				.map(this::sanitizeRecipe)
				.filter(keepRecipeWithValidIngredients(correlationId, url))
				.toList();
	}

	private RecipeExtractedFromInput sanitizeRecipe(RecipeExtractedFromInput recipe) {
		return new RecipeExtractedFromInput(
				toNonBlankPlainText(recipe.name()),
				toNonBlankPlainText(recipe.recipeYield()),
				toNonBlankPlainTexts(recipe.recipeIngredients()),
				toNonBlankPlainTexts(recipe.recipeInstructions()),
				toNonBlankPlainText(recipe.text())
		);
	}

	private Optional<String> toNonBlankPlainText(Optional<String> html) {
		return html.map(this::toPlainText).filter(text -> !text.isBlank());
	}

	private List<String> toNonBlankPlainTexts(List<String> htmls) {
		return htmls.stream().filter(Objects::nonNull).map(this::toPlainText).filter(text -> !text.isBlank()).toList();
	}

	/**
	 * Recipe texts in JSON-LD are HTML on many sites: they carry entities (e.g. {@code &#39;}, {@code &nbsp;}) and
	 * sometimes markup (e.g. {@code <p>}, {@code <br>}). The content of a {@code <script>} element is never decoded,
	 * so they arrive verbatim, both from the renderer and from the clients that read JSON-LD themselves.
	 */
	private String toPlainText(String html) {
		return Jsoup.parseBodyFragment(html).text();
	}

	private Predicate<? super RecipeExtractedFromInput> keepRecipeWithValidIngredients(UUID correlationId, String url) {
		return recipe -> {
			if (recipe.recipeIngredients().isEmpty()) {
				LOG.warn("Recipe contains no ingredients <{}> {} in {}", correlationId, recipe.name(), url);
				return false;
			} else {
				return true;
			}
		};
	}

	private List<RecipeExtractedFromInput> parseJsonLdRecipes(String jsonLdContent) throws JacksonException {
		JsonNode root = objectMapper.readTree(jsonLdContent);
		List<JsonNode> recipeNodes = new ArrayList<>();
		collectRecipeNodes(root, recipeNodes);
		return recipeNodes.stream().map(this::toRecipeExtractedFromInput).toList();
	}

	private void collectRecipeNodes(JsonNode node, List<JsonNode> recipeNodes) {
		if (node == null || node.isNull()) {
			return;
		}
		if (isRecipeNode(node)) {
			recipeNodes.add(node);
		}
		if (node.isArray()) {
			node.forEach(item -> collectRecipeNodes(item, recipeNodes));
		} else if (node.isObject()) {
			node.properties().forEach(entry -> collectRecipeNodes(entry.getValue(), recipeNodes));
		}
	}

	private boolean isRecipeNode(JsonNode node) {
		if (!node.isObject()) {
			return false;
		}
		JsonNode typeNode = node.get("@type");
		if (typeNode == null || typeNode.isNull()) {
			return false;
		}
		if (typeNode.isString()) {
			return isRecipeType(typeNode.stringValue());
		}
		if (typeNode.isArray()) {
			for (JsonNode item : typeNode) {
				if (item.isString() && isRecipeType(item.stringValue())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRecipeType(String type) {
		return type != null && ("Recipe".equals(type) || type.endsWith("/Recipe") || type.endsWith(":Recipe"));
	}

	private RecipeExtractedFromInput toRecipeExtractedFromInput(JsonNode recipeNode) {
		return new RecipeExtractedFromInput(
				optionalText(recipeNode.get("name")),
				optionalText(recipeNode.get("recipeYield")),
				ingredients(recipeNode),
				instructions(recipeNode.get("recipeInstructions")),
				optionalText(recipeNode.get("description"))
		);
	}

	private Optional<String> optionalText(JsonNode node) {
		if (node == null || node.isNull()) {
			return Optional.empty();
		}
		String text = recipeJsonCoercionStrategy.coerce(node, objectMapper).trim();
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	private List<String> ingredients(JsonNode recipeNode) {
		JsonNode ingredientsNode = recipeNode.get("recipeIngredient");
		if (ingredientsNode == null || ingredientsNode.isNull()) {
			ingredientsNode = recipeNode.get("ingredients");
		}
		return stringsFromNode(ingredientsNode);
	}

	private List<String> instructions(JsonNode instructionsNode) {
		return stringsFromNode(instructionsNode);
	}

	private List<String> stringsFromNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(item -> {
				String value = recipeJsonCoercionStrategy.coerce(item, objectMapper).trim();
				if (!value.isEmpty()) {
					values.add(value);
				}
			});
			return values;
		}
		String value = recipeJsonCoercionStrategy.coerce(node, objectMapper).trim();
		return value.isEmpty() ? List.of() : List.of(value);
	}
}
