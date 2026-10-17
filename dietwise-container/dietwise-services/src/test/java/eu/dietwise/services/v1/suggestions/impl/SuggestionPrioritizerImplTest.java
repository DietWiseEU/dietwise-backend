package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.TypeOfRecommendation;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuggestionPrioritizerImplTest {
	private static final Recommendation HIGH_RECOMMENDATION = new RecommendationImpl("high");
	private static final Recommendation LOW_RECOMMENDATION = new RecommendationImpl("low");
	private static final Recommendation MISSING_RECOMMENDATION = new RecommendationImpl("missing");
	private static final IngredientId HIGH_INGREDIENT_ID = new GenericIngredientId("ingredient-high");
	private static final IngredientId LOW_INGREDIENT_ID = new GenericIngredientId("ingredient-low");
	private static final IngredientId MISSING_INGREDIENT_ID = new GenericIngredientId("ingredient-missing");
	private static final IngredientId WITHOUT_PRIMARY_INGREDIENT_ID = new GenericIngredientId("ingredient-without-primary");
	// The Recommendation of a Suggestion is translated for display; the weights are keyed by the master Recommendation.
	private static final Suggestion HIGH_SUGGESTION = suggestion("high", HIGH_INGREDIENT_ID);
	private static final Suggestion LOW_SUGGESTION = suggestion("low", LOW_INGREDIENT_ID);
	private static final Suggestion MISSING_SUGGESTION = suggestion("missing", MISSING_INGREDIENT_ID);
	private static final Suggestion WITHOUT_PRIMARY_SUGGESTION = suggestion("without-primary", WITHOUT_PRIMARY_INGREDIENT_ID);
	private static final List<Suggestion> INPUT_SUGGESTIONS = List.of(LOW_SUGGESTION, WITHOUT_PRIMARY_SUGGESTION, HIGH_SUGGESTION, MISSING_SUGGESTION);
	private static final Map<IngredientId, RecommendationComponent> PRIMARY_COMPONENTS = Map.of(
			HIGH_INGREDIENT_ID, component(HIGH_RECOMMENDATION),
			LOW_INGREDIENT_ID, component(LOW_RECOMMENDATION),
			MISSING_INGREDIENT_ID, component(MISSING_RECOMMENDATION)
	);
	private static final Map<Recommendation, BigDecimal> WEIGHTS = Map.of(
			HIGH_RECOMMENDATION, BigDecimal.valueOf(0.9),
			LOW_RECOMMENDATION, BigDecimal.valueOf(0.2)
	);
	private static final PersonalInfo PERSONAL_INFO = ImmutablePersonalInfo.builder()
			.yearOfBirth(1996)
			.gender(BiologicalGender.FEMALE)
			.build();

	@Mock
	private RecommendationService recommendationService;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory =
			new MockReactivePersistenceContextFactory();

	@Test
	void ordersTheSuggestionsByTheWeightOfThePrimaryComponentOfTheirIngredientHighestFirst() {
		var sut = new SuggestionPrioritizerImpl(recommendationService);
		when(recommendationService.findRecommendationWeights(any(), eq(PERSONAL_INFO))).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, PERSONAL_INFO, INPUT_SUGGESTIONS, PRIMARY_COMPONENTS))
				.await().indefinitely();

		// A Recommendation the user has no weight for, or an ingredient without a primary component, weighs nothing, so
		// its Suggestion ranks last.
		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, WITHOUT_PRIMARY_SUGGESTION, MISSING_SUGGESTION);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private static Suggestion suggestion(String suffix, IngredientId ingredientId) {
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId("suggestion-" + suffix))
				.alternative(new AlternativeIngredientImpl("alternative-" + suffix))
				.target(new AppliesTo.AppliesToIngredient(ingredientId))
				.ruleId(new GenericRuleId("rule-" + suffix))
				.recommendation(new RecommendationImpl("translated-" + suffix))
				.text("text-" + suffix)
				.build();
	}

	private static RecommendationComponent component(Recommendation recommendation) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(recommendation)
				.componentForScoring(new RecommendationComponentNameImpl("component-" + recommendation.asString()))
				.typeOfRecommendation(TypeOfRecommendation.ENCOURAGED)
				.build();
	}
}
