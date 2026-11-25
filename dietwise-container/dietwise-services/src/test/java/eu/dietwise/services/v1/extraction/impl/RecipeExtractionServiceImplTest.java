package eu.dietwise.services.v1.extraction.impl;

import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecipeExtractionServiceImplTest {
	private static final UUID CORRELATION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String URL = "https://example.test/recipe";
	private static final RecipeExtractedFromInput RECIPE_SIMPLE_PASTA = new RecipeExtractedFromInput(
			Optional.of("Simple Pasta"),
			Optional.empty(),
			List.of("200g pasta", "salt"),
			List.of("Boil water", "Cook pasta"),
			Optional.of("Recipe text")
	);
	private static final RecipeExtractedFromInput RECIPE_SIMPLE_SALAD = new RecipeExtractedFromInput(
			Optional.of("Simple Salad"),
			Optional.empty(),
			List.of("lettuce", "olive oil"),
			List.of("Mix", "Serve"),
			Optional.empty()
	);
	private static final RecipeExtractedFromInput RECIPE_WITHOUT_INGREDIENTS = new RecipeExtractedFromInput(
			Optional.of("Ingredient-less recipe"),
			Optional.empty(),
			List.of(),
			List.of("Step one"),
			Optional.empty()
	);
	private static final RecipeExtractedFromInput RECIPE_WITH_HTML = new RecipeExtractedFromInput(
			Optional.of("Chef John&#39;s Italian Meatballs"),
			Optional.of("4&nbsp;servings"),
			List.of("1/2 c. (60 g.)&nbsp;all-purpose flour", "2 jars Tomato &amp; Basil sauce"),
			List.of("Pork: <BR>1) Preheat oven to 160C/Gas 3. <BR><BR>2) Pour beer in bottom of baking pan.", "Bake 12 minutes (don&#39;t turn)."),
			Optional.of("<p>Chicken Pot Pie Soup.</p>\n<p>From <em>Barefoot Contessa</em>.</p>")
	);
	private static final RecipeExtractedFromInput RECIPE_WITH_TEXTS_THAT_ARE_ONLY_HTML = new RecipeExtractedFromInput(
			Optional.of("<br>"),
			Optional.of("&nbsp;"),
			List.of("200g pasta", "<p></p>", "&nbsp;"),
			List.of("<br>", "Cook pasta"),
			Optional.of("<p> </p>")
	);
	private static final String PLAIN_NAME = "Chef John's Italian Meatballs";
	private static final String PLAIN_YIELD = "4 servings";
	private static final List<String> PLAIN_INGREDIENTS = List.of("1/2 c. (60 g.) all-purpose flour", "2 jars Tomato & Basil sauce");
	private static final List<String> PLAIN_INSTRUCTIONS = List.of("Pork: 1) Preheat oven to 160C/Gas 3. 2) Pour beer in bottom of baking pan.", "Bake 12 minutes (don't turn).");
	private static final String PLAIN_TEXT = "Chicken Pot Pie Soup. From Barefoot Contessa.";
	private static final RecipeExtractionAndAssessmentParam URL_EXTRACTION_PARAM = ImmutableRecipeExtractionAndAssessmentParam.builder()
			.url(URL)
			.lang(RecipeLanguage.EN)
			.build();
	private static final String MARKDOWN = "Simple pasta recipe with ingredients and instructions.";
	private static final String JSONLD_CONTENT = """
			{"@context":"https://schema.org","@type":"Recipe","name":"Simple Pasta","recipeIngredient":["200g pasta","salt"],"recipeInstructions":["Boil water","Cook pasta"]}
			""";
	private static final String JSONLD_CONTENT_WITH_HTML = """
			{"@context":"https://schema.org","@type":"Recipe",\
			"name":"Chef John&#39;s Italian Meatballs",\
			"recipeYield":"4&nbsp;servings",\
			"recipeIngredient":["1/2 c. (60 g.)&nbsp;all-purpose flour","2 jars Tomato &amp; Basil sauce"],\
			"recipeInstructions":["Pork: <BR>1) Preheat oven to 160C/Gas 3. <BR><BR>2) Pour beer in bottom of baking pan.","Bake 12 minutes (don&#39;t turn)."],\
			"description":"<p>Chicken Pot Pie Soup.</p>\\n<p>From <em>Barefoot Contessa</em>.</p>"}
			""";
	private static final String INVALID_JSONLD_CONTENT = "{this is not valid json-ld}";
	private static final RecipeAssessmentParam MARKDOWN_PARAM = ImmutableRecipeAssessmentParam.builder()
			.url(URL)
			.lang(RecipeLanguage.EN)
			.pageContent(MARKDOWN)
			.build();
	private static final RecipeAssessmentParam MARKDOWN_PARAM_WITH_JSONLD = ImmutableRecipeAssessmentParam.builder()
			.url(URL)
			.lang(RecipeLanguage.EN)
			.pageContent(MARKDOWN)
			.jsonLdContent(JSONLD_CONTENT)
			.build();
	private static final RecipeAssessmentParam MARKDOWN_PARAM_WITH_HTML_IN_JSONLD = ImmutableRecipeAssessmentParam.builder()
			.url(URL)
			.lang(RecipeLanguage.EN)
			.pageContent(MARKDOWN)
			.jsonLdContent(JSONLD_CONTENT_WITH_HTML)
			.build();
	private static final RecipeAssessmentParam MARKDOWN_PARAM_WITH_INVALID_JSONLD = ImmutableRecipeAssessmentParam.builder()
			.url(URL)
			.lang(RecipeLanguage.EN)
			.pageContent(MARKDOWN)
			.jsonLdContent(INVALID_JSONLD_CONTENT)
			.build();
	private static final String RENDERED_MARKDOWN = "rendered markdown";

	@Mock
	private RecipeFilterAiFacade filterAiFacade;

	@Mock
	private MarkdownRecipeExtractionService extractionService;

	@Mock
	private RendererClient rendererClient;

	private RecipeExtractionServiceImpl makeSut() {
		return new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient, new ObjectMapper(), new KeyValuePairsCoercionStrategy());
	}

	private RecipeExtractionServiceImpl makeSut(boolean allowCachedTestPages) {
		return new RecipeExtractionServiceImpl(filterAiFacade, extractionService, allowCachedTestPages, rendererClient, new ObjectMapper(), new KeyValuePairsCoercionStrategy());
	}

	@Test
	void useAiToExtractRecipeFromMarkdownEmitsExtractionMessage() {
		var sut = makeSut();

		when(filterAiFacade.filterRecipeBlock(RecipeLanguage.EN, MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RecipeLanguage.EN, MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.useAiToExtractRecipeFromMarkdown(CORRELATION_ID, URL, RecipeLanguage.EN, MARKDOWN).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		verify(filterAiFacade).filterRecipeBlock(RecipeLanguage.EN, MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RecipeLanguage.EN, MARKDOWN);
	}

	@Test
	void extractRecipeFromJsonLdOrMarkdownUsesJsonLdContentWhenPresentAndUseful() {
		var sut = makeSut();

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromJsonLdOrMarkdown(CORRELATION_ID, MARKDOWN_PARAM_WITH_JSONLD).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");

		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromJsonLdOrMarkdownFallsBackToAiWhenJsonLdContentIsInvalid() {
		var sut = makeSut();
		when(filterAiFacade.filterRecipeBlock(RecipeLanguage.EN, MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RecipeLanguage.EN, MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromJsonLdOrMarkdown(CORRELATION_ID, MARKDOWN_PARAM_WITH_INVALID_JSONLD).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);

		verify(filterAiFacade).filterRecipeBlock(RecipeLanguage.EN, MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RecipeLanguage.EN, MARKDOWN);
	}

	@Test
	void extractRecipeFromJsonLdOrMarkdownConvertsRecipeTextsFromHtmlToPlainText() {
		var sut = makeSut();

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromJsonLdOrMarkdown(CORRELATION_ID, MARKDOWN_PARAM_WITH_HTML_IN_JSONLD).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		assertRecipeHasPlainTexts(extractionMessage.recipes().getFirst().recipe());
	}

	@Test
	void extractRecipeFromUrlEmitsExtractionMessage() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromUrlConvertsRecipeTextsFromHtmlToPlainText() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_WITH_HTML));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		assertRecipeHasPlainTexts(extractionMessage.recipes().getFirst().recipe());
	}

	@ParameterizedTest
	@MethodSource("htmlAndPlainText")
	void extractRecipeFromUrlConvertsHtmlToPlainText(String html, String plainText) {
		var sut = makeSut();
		var recipe = new RecipeExtractedFromInput(Optional.empty(), Optional.empty(), List.of(html), List.of(), Optional.empty());
		var restResponse = makeRenderResponseForRecipes(List.of(recipe));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly(plainText);
	}

	private static Stream<Arguments> htmlAndPlainText() {
		return Stream.of(
				arguments("Chef John&#39;s", "Chef John's"),
				arguments("it&#x2019;s", "it’s"),
				arguments("don&apos;t", "don't"),
				arguments("180&deg;C &frac12; cup", "180°C ½ cup"),
				arguments("1/2 c. (60 g.)&nbsp;flour", "1/2 c. (60 g.) flour"),
				arguments("salt&nbsp;&nbsp;", "salt"),
				arguments("<strong>1 cup</strong> flour", "1 cup flour"),
				arguments("<p>1 cup</p><p>flour</p>", "1 cup flour"),
				arguments("salt<br>pepper", "salt pepper"),
				arguments("Salt & pepper", "Salt & pepper"),
				arguments("less than < 5 g sugar", "less than < 5 g sugar"),
				arguments("&lt;p&gt; is decoded once", "<p> is decoded once")
		);
	}

	@Test
	void extractRecipeFromUrlDropsTextsLeftBlankByPlainTextConversion() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_WITH_TEXTS_THAT_ARE_ONLY_HTML));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		Recipe recipe = extractionMessage.recipes().getFirst().recipe();
		assertThat(recipe.getName()).isEmpty();
		assertThat(recipe.getRecipeYield()).isEmpty();
		assertThat(recipe.getRecipeIngredients()).extracting(Ingredient::getNameInRecipe).containsExactly("200g pasta");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Cook pasta");
		assertThat(recipe.getText()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://localhost/recipe",
			"http://127.0.0.1/recipe",
			"http://10.0.0.1/recipe",
			"http://[::1]/recipe",
			"file:///tmp/recipe.txt",
			"https://user:pass@example.test/recipe"
	})
	void extractRecipeFromUrlRejectsUnsafeUrls(String url) {
		var sut = makeSut();
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url(url)
				.lang(RecipeLanguage.EN)
				.build();

		assertThatThrownBy(() -> sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(InvalidRecipeSourceUrlException.class);

		verifyNoInteractions(rendererClient);
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromUrlAllowsExactCachedTestPageWhenEnabled() {
		var sut = makeSut(true);
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url("001.html")
				.lang(RecipeLanguage.EN)
				.build();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		verify(rendererClient).render(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"1.html",
			"0001.html",
			"001.HTML",
			"001.html?x=1",
			"/001.html",
			"foo/001.html"
	})
	void extractRecipeFromUrlRejectsNearMissCachedTestPagesEvenWhenEnabled(String url) {
		var sut = makeSut(true);
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url(url)
				.lang(RecipeLanguage.EN)
				.build();

		assertThatThrownBy(() -> sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(InvalidRecipeSourceUrlException.class);

		verifyNoInteractions(rendererClient);
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void extractRecipeFromUrlFallsBackToAiWhenNoJsonLdRecipes(List<RecipeExtractedFromInput> recipes) {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(recipes);
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiFacade.filterRecipeBlock(RecipeLanguage.EN, RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RecipeLanguage.EN, RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		verify(rendererClient).render(any());
		verify(filterAiFacade).filterRecipeBlock(RecipeLanguage.EN, RENDERED_MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RecipeLanguage.EN, RENDERED_MARKDOWN);
	}

	@Test
	void extractRecipeFromUrlEmitsMoreThanOneRecipesMessageWhenMultipleRecipesExtracted() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA, RECIPE_SIMPLE_SALAD));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(2);

		verify(rendererClient).render(any());
	}

	@Test
	void extractRecipeFromUrlKeepsOnlyValidJsonLdRecipesWhenInputIsMixed() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA, RECIPE_WITHOUT_INGREDIENTS));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromUrlEmitsErrorWhenAiReturnsRecipeWithoutIngredients() {
		var sut = makeSut();
		var restResponse = makeRenderResponseForRecipes(Collections.emptyList());
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiFacade.filterRecipeBlock(RecipeLanguage.EN, RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RecipeLanguage.EN, RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_WITHOUT_INGREDIENTS));

		UniAssertSubscriber<RecipeExtractionRecipeAssessmentMessage> subscriber =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM)
						.subscribe().withSubscriber(UniAssertSubscriber.create());

		subscriber.awaitFailure().assertFailedWith(NoIngredientsInRecipeException.class);

		verify(rendererClient).render(any());
		verify(filterAiFacade).filterRecipeBlock(RecipeLanguage.EN, RENDERED_MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RecipeLanguage.EN, RENDERED_MARKDOWN);
	}

	@Test
	void extractRecipeFromUrlEmitsErrorWhenRendererFails() {
		var sut = makeSut();
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("Renderer failed")));

		UniAssertSubscriber<RecipeExtractionRecipeAssessmentMessage> subscriber =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM)
						.subscribe().withSubscriber(UniAssertSubscriber.create());

		subscriber.awaitFailure().assertFailedWith(RuntimeException.class, "Renderer failed");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	private static void assertRecipeHasPlainTexts(Recipe recipe) {
		assertThat(recipe.getName()).contains(PLAIN_NAME);
		assertThat(recipe.getRecipeYield()).contains(PLAIN_YIELD);
		assertThat(recipe.getRecipeIngredients()).extracting(Ingredient::getNameInRecipe).containsExactlyElementsOf(PLAIN_INGREDIENTS);
		assertThat(recipe.getRecipeInstructions()).containsExactlyElementsOf(PLAIN_INSTRUCTIONS);
		assertThat(recipe.getText()).contains(PLAIN_TEXT);
	}

	private RestResponse<RenderResponse> makeRenderResponseForRecipes(List<RecipeExtractedFromInput> recipes) {
		var renderResponse = new RenderResponse(
				RENDERED_MARKDOWN,
				recipes,
				"https://example.test/recipe",
				null
		);
		@SuppressWarnings("unchecked")
		RestResponse<RenderResponse> restResponse = (RestResponse<RenderResponse>) mock(RestResponse.class);
		when(restResponse.getEntity()).thenReturn(renderResponse);
		return restResponse;
	}
}
