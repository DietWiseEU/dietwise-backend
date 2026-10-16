package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.i18n.I18nMessages;
import eu.dietwise.services.v1.suggestions.MakeSuggestionsResult;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeSuggestionsServiceImpl implements RecipeSuggestionsService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeSuggestionsServiceImpl.class);

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final PersonalInfoDao personalInfoDao;
	private final SuggestionsAiFacade suggestionsAiFacade;
	private final SuggestionDao suggestionDao;
	private final RuleDao ruleDao;
	private final SuggestionPrioritizer suggestionPrioritizer;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	private final I18nMessages i18nMessages;

	public RecipeSuggestionsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			PersonalInfoDao personalInfoDao,
			SuggestionsAiFacade suggestionsAiFacade,
			SuggestionDao suggestionDao, RuleDao ruleDao,
			SuggestionPrioritizer suggestionPrioritizer,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao,
			I18nMessages i18nMessages
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.personalInfoDao = personalInfoDao;
		this.suggestionsAiFacade = suggestionsAiFacade;
		this.suggestionDao = suggestionDao;
		this.ruleDao = ruleDao;
		this.suggestionPrioritizer = suggestionPrioritizer;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
		this.i18nMessages = i18nMessages;
	}

	@Override
	public Uni<MakeSuggestionsResult> makeSuggestions(UUID correlationId, HasUserId hasUserId, RecipeLanguage lang, Recipe recipe, Country countryOverride) {
		return persistenceContextFactory.withTransaction(tx -> makeSuggestions(tx, correlationId, hasUserId, lang, recipe, countryOverride));
	}

	private Uni<MakeSuggestionsResult> makeSuggestions(ReactivePersistenceTxContext tx, UUID correlationId, HasUserId hasUserId, RecipeLanguage lang, Recipe recipe, Country countryOverride) {
		return forcm(
				readAllNecessaryData(tx, hasUserId, lang, countryOverride),
				extractSuggestionsForRecipePerIngredient(tx, correlationId, lang, recipe),
				prioritizeSuggestions(tx),
				(necessaryData, _, data) -> new MakeSuggestionsResult(
						new SuggestionsRecipeAssessmentMessage(data.suggestions()),
						data.recommendations(),
						data.primaryComponents(),
						necessaryData.personalInfo()
				)
		);
	}

	private Uni<RecipeSuggestionNecessaryData> readAllNecessaryData(ReactivePersistenceTxContext tx, HasUserId hasUserId, RecipeLanguage lang, Country countryOverride) {
		return forcm(
				personalInfoDao.findByUser(tx, hasUserId).map(personalInfo -> applyCountryOverride(personalInfo, countryOverride)),
				_ -> suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(tx, lang),
				RecipeSuggestionNecessaryData::new
		);
	}

	private PersonalInfo applyCountryOverride(PersonalInfo personalInfo, Country countryOverride) {
		if (countryOverride == null) {
			return personalInfo;
		} else if (personalInfo == null) {
			return ImmutablePersonalInfo.builder().country(countryOverride).build();
		} else {
			return ImmutablePersonalInfo.copyOf(personalInfo).withCountry(countryOverride);
		}
	}

	private Function<? super RecipeSuggestionNecessaryData, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> extractSuggestionsForRecipePerIngredient(
			ReactivePersistenceTxContext tx,
			UUID correlationId,
			RecipeLanguage lang,
			Recipe recipe
	) {
		return data -> Multi.createFrom().iterable(recipe.getRecipeIngredients())
				.onItem().transformToUniAndConcatenate(processIngredient(tx, correlationId, recipe, lang, data))
				.collect()
				.in(
						SuggestionsAndRecommendationsPerIngredient::emptyMutable,
						(acc, cur) -> {
							if (cur.ingredient() != null) {
								acc.suggestions().addAll(cur.suggestions());
								acc.recommendations().put(cur.ingredient().getId(), cur.components());
								if (cur.primaryComponent() != null) {
									acc.primaryComponents().put(cur.ingredient().getId(), cur.primaryComponent());
								}
							}
						}
				);
	}

	private Function<? super Ingredient, Uni<? extends SuggestionsAndComponents>> processIngredient(
			ReactivePersistenceTxContext tx,
			UUID correlationId,
			Recipe recipe,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data
	) {
		return ingredient -> forc(
				determineRoleOrTechnique(correlationId, recipe, lang, data, ingredient),
				determineTriggerIngredient(correlationId, lang, data, ingredient),
				(role, triggerIngredient) -> processIngredientOfTriggerIngredient(tx, correlationId, recipe, lang, data, ingredient, role, triggerIngredient)
		).onFailure(TriggerIngredientFromAiNotInDbException.class).recoverWithUni(() -> processIngredientWithoutTriggerIngredient(correlationId, lang, data, ingredient))
		.onFailure(NonFatalIngredientProcessingException.class).recoverWithItem(t -> {
			LOG.warn("Failed to process ingredient <{}>: {} -> {}", correlationId, ingredient.getNameInRecipe(), t.getMessage());
			return SuggestionsAndComponents.empty(ingredient);
		});
	}

	/**
	 * An ingredient that matches no Trigger Ingredient suggests nothing; the AI determines the components it contributes
	 * to the score of the recipe.
	 */
	private Uni<SuggestionsAndComponents> processIngredientWithoutTriggerIngredient(
			UUID correlationId,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient
	) {
		String availableRecommendationsAsMarkdownList = suggestionsAiFacade.convertRecommendationsToMarkdownList(data.recommendations().values());
		return suggestionsAiFacade.matchIngredientsWithRecommendations(lang, availableRecommendationsAsMarkdownList, ingredient.getNameInRecipe())
				.map(componentNames -> {
					LOG.info("matchIngredientsWithRecommendations without trigger ingredient for <{}>: {} -> {}", correlationId, ingredient.getNameInRecipe(), componentNames);
					Set<RecommendationComponent> components = componentNames.stream()
							.map(name -> data.recommendations().get(name.trim().toLowerCase()))
							.filter(Objects::nonNull)
							.collect(Collectors.toSet());
					return SuggestionsAndComponents.withoutSuggestions(ingredient, components);
				});
	}

	/**
	 * The ingredient contributes the components of its Trigger Ingredient to the score of the recipe, whether or not
	 * there are Rules to suggest alternatives by. A Trigger Ingredient without active Rules only contributes to the score.
	 */
	private Uni<SuggestionsAndComponents> processIngredientOfTriggerIngredient(
			ReactivePersistenceTxContext tx,
			UUID correlationId,
			Recipe recipe,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient,
			RoleOrTechnique role,
			TriggerIngredient triggerIngredient
	) {
		Set<RecommendationComponent> components = data.componentsOf(triggerIngredient.getComponentsForScoring());
		return ruleDao.findByTriggerIngredient(tx, triggerIngredient, lang).flatMap(rules -> {
			if (rules.isEmpty()) {
				LOG.info("No active rules, scoring only for <{}>: {} -> {}", correlationId, ingredient.getNameInRecipe(), triggerIngredient.getName());
				return Uni.createFrom().item(SuggestionsAndComponents.withoutSuggestions(ingredient, components));
			} else {
				return suggestAlternativesByBestFittingRule(tx, correlationId, recipe, lang, data, ingredient, role, triggerIngredient, rules, components)
						.onFailure(NonFatalIngredientProcessingException.class).recoverWithItem(t -> {
							LOG.warn("Failed to suggest alternatives for ingredient <{}>: {} -> {}", correlationId, ingredient.getNameInRecipe(), t.getMessage());
							return SuggestionsAndComponents.withoutSuggestions(ingredient, components);
						});
			}
		});
	}

	private Uni<SuggestionsAndComponents> suggestAlternativesByBestFittingRule(
			ReactivePersistenceTxContext tx,
			UUID correlationId,
			Recipe recipe,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient,
			RoleOrTechnique role,
			TriggerIngredient triggerIngredient,
			List<Rule> rules,
			Set<RecommendationComponent> components
	) {
		return forc(
				findBestRuleAndDetermineFromStringId(lang, ingredient, role, triggerIngredient, components, rules),
				rule -> loadAlternativesFromDbAndSelectBest(tx, correlationId, lang, recipe.getName().orElse("-"), data, ingredient, role, rule),
				(rule, suggestions) -> Uni.createFrom().item(withPrimaryComponent(
						ingredient, withGeneratedText(suggestions, ingredient, lang), components, data.componentsByRecommendation().get(rule.getRecommendation())))
		);
	}

	private Uni<Rule> findBestRuleAndDetermineFromStringId(
			RecipeLanguage lang,
			Ingredient ingredient,
			RoleOrTechnique role,
			TriggerIngredient triggerIngredient,
			Set<RecommendationComponent> components,
			List<Rule> rules
	) {
		return suggestionsAiFacade.findBestRule(lang, ingredient.getNameInRecipe(), role, triggerIngredient, components, rules)
				.map(determineRuleFromStringId(rules));
	}

	private static SuggestionsAndComponents withPrimaryComponent(
			Ingredient ingredient,
			List<Suggestion> suggestions,
			Set<RecommendationComponent> components,
			RecommendationComponent primaryComponent
	) {
		if (primaryComponent == null) {
			return new SuggestionsAndComponents(ingredient, suggestions, components, null);
		} else {
			Set<RecommendationComponent> withPrimary = new HashSet<>(components);
			withPrimary.add(primaryComponent);
			return new SuggestionsAndComponents(ingredient, suggestions, withPrimary, primaryComponent);
		}
	}

	private Uni<RoleOrTechnique> determineRoleOrTechnique(
			UUID correlationId,
			Recipe recipe,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient
	) {
		String rolesMarkdownList = suggestionsAiFacade.convertRolesToMarkdownList(data.roles().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		String instructionsAsMarkdownList = suggestionsAiFacade.convertInstructionsToMarkdownList(recipe.getRecipeInstructions());
		return suggestionsAiFacade.assessIngredientRole(lang, rolesMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList)
				.invoke(r -> LOG.info("assessIngredientRole for <{}>: {} -> {}", correlationId, ingredientNameInRecipe, r))
				.map(data.roles()::get);
	}

	private Function<? super RoleOrTechnique, Uni<? extends TriggerIngredient>> determineTriggerIngredient(
			UUID correlationId,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient
	) {
		String availableTriggerIngredientsAsMarkdownList = suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(data.triggerIngredients().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		return role -> suggestionsAiFacade.matchIngredientToTrigger(lang, availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role)
				.flatMap(triggerIngredientFromAi -> {
					LOG.info("matchIngredientToTrigger for <{}>: {} -> {}", correlationId, ingredientNameInRecipe, triggerIngredientFromAi);
					TriggerIngredient triggerIngredient = data.triggerIngredients().get(triggerIngredientFromAi);
					return triggerIngredient == null
							? Uni.createFrom().failure(() -> new TriggerIngredientFromAiNotInDbException(ingredient, triggerIngredientFromAi))
							: Uni.createFrom().item(triggerIngredient);
				});
	}

	private Function<String, Rule> determineRuleFromStringId(List<Rule> rules) {
		return ruleId -> {
			String normalizedRuleId = ruleId.trim().toLowerCase();
			return rules.stream().filter(r -> r.getId().asString().toLowerCase().equals(normalizedRuleId)).findFirst()
					.orElseThrow(() -> new NoMatchingRuleException(ruleId));
		};
	}

	private Uni<List<Suggestion>> loadAlternativesFromDbAndSelectBest(
			ReactivePersistenceTxContext tx,
			UUID correlationId,
			RecipeLanguage lang,
			String recipeName,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient,
			RoleOrTechnique role,
			Rule rule
	) {
		return forc(
				suggestionDao.retrieveByRule(tx, rule, country(data.personalInfo()), ingredient, lang),
				suggestions -> suggestionsAiFacade.suggestAlternatives(lang, recipeName, ingredient.getNameInRecipe(), role, suggestions),
				(suggestions, responseFromAi) -> {
					if (LOG.isDebugEnabled()) {
						var suggestionsStr = suggestions.stream().map(Suggestion::getAlternative).map(RepresentableAsString::asString).collect(Collectors.joining(","));
						LOG.debug("Suggest alternatives for <{}> {}, initial list is {}:\n{}", correlationId, ingredient.getNameInRecipe(), suggestionsStr, responseFromAi);
					}
					return Uni.createFrom().item(selectSuggestionsFromAiResponse(suggestions, responseFromAi));
				}
		);
	}

	static List<Suggestion> selectSuggestionsFromAiResponse(List<Suggestion> suggestions, String responseFromAi) {
		Set<String> alternativesFromAi = responseFromAi == null ? Set.of() : responseFromAi.lines()
				.map(RecipeSuggestionsServiceImpl::normalizeAlternative)
				.filter(line -> !line.isEmpty())
				.collect(Collectors.toSet());
		List<Suggestion> selected = suggestions.stream()
				.filter(s -> alternativesFromAi.contains(normalizeAlternative(s.getAlternative().asString())))
				.toList();
		// The AI only refines an already curated list; if it gave us nothing we recognize
		// (blank, or no matching name), keep all candidates rather than dropping them.
		return selected.isEmpty() ? suggestions : selected;
	}

	private static String normalizeAlternative(String value) {
		return value.replaceFirst("^\\s*[-*]\\s+", "").trim().toLowerCase();
	}

	private Country country(PersonalInfo personalInfo) {
		return personalInfo == null ? null : personalInfo.getCountry();
	}

	private String toLogString(RoleOrTechnique role) {
		return role == null ? "null" : role.getName() + " (" + role.getId().asString() + ")";
	}

	private String toLogString(TriggerIngredient triggerIngredient) {
		return triggerIngredient == null ? "null" : triggerIngredient.getName() + " (" + triggerIngredient.getId().asString() + ")";
	}

	// TODO Dummy for now, implement to fill-in the text - filtering happens in loadAlternativesFromDbAndSelectBest
	private List<Suggestion> withGeneratedText(List<Suggestion> suggestions, Ingredient ingredient, RecipeLanguage lang) {
		return suggestions.stream()
				.map(s -> (Suggestion) ImmutableSuggestion.copyOf(s).withText(i18nMessages.format(
						lang,
						"suggestion.text.weSuggestInsteadOf",
						s.getAlternative().asString(),
						ingredient.getNameInRecipe()
				)))
				.toList();
	}

	private BiFunction<? super RecipeSuggestionNecessaryData, ? super SuggestionsAndRecommendationsPerIngredient, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> prioritizeSuggestions(ReactivePersistenceTxContext tx) {
		return (data, suggestionsAndRecommendations) -> suggestionPrioritizer.prioritizeSuggestions(tx, data.personalInfo(), suggestionsAndRecommendations.suggestions())
				.map(suggestionsAndRecommendations::withSuggestions);
	}

	@Override
	public Uni<Void> increaseTimesSuggested(UUID correlationId, String applicationId, HasUserId hasUserId, HasSuggestionTemplateIds suggestions) {
		return persistenceContextFactory.withTransaction(tx ->
				Multi.createFrom().iterable(suggestions.getSuggestionTemplateIds())
						.onItem().transformToUniAndConcatenate(sid -> userSuggestionStatsEntityDao.increaseTimesSuggested(tx, applicationId, hasUserId, sid))
						.onItem().ignoreAsUni()
						.onFailure()
						.recoverWithItem(t -> {
							LOG.error("Failed to increase times suggested for user <{}> {}: {}", correlationId, hasUserId, t.getMessage(), t);
							return null;
						})
		);
	}

	@Override
	public Uni<SuggestionsRecipeAssessmentMessage> enrichWithStatistics(UUID correlationId, String applicationId, HasUserId hasUserId, SuggestionsRecipeAssessmentMessage message) {
		Set<SuggestionTemplateId> suggestionIds = message.getSuggestionTemplateIds();
		return persistenceContextFactory.withoutTransaction(em ->
				forcm(
						userSuggestionStatsEntityDao.retrieveUserSuggestionStats(em, applicationId, hasUserId, suggestionIds),
						_ -> userSuggestionStatsEntityDao.retrieveTotalSuggestionStats(em, applicationId, suggestionIds),
						(u, t) -> enrichWithStatistics(message, u, t)
				)
		);
	}

	private SuggestionsRecipeAssessmentMessage enrichWithStatistics(
			SuggestionsRecipeAssessmentMessage message,
			Map<SuggestionTemplateId, SuggestionStats> userStats,
			Map<SuggestionTemplateId, SuggestionStats> totalStats
	) {
		List<Suggestion> enrichedSuggestions = message.suggestions().stream().map(s -> {
			SuggestionStats userStatsForSuggestion = userStats.getOrDefault(s.getId(), SuggestionStats.ALL_ZEROES);
			SuggestionStats totalStatsForSuggestion = totalStats.getOrDefault(s.getId(), SuggestionStats.ALL_ZEROES);
			return (Suggestion) ImmutableSuggestion.builder().from(s).userSuggestionStats(userStatsForSuggestion).totalSuggestionStats(totalStatsForSuggestion).build();
		}).toList();
		return new SuggestionsRecipeAssessmentMessage(enrichedSuggestions);
	}
}
