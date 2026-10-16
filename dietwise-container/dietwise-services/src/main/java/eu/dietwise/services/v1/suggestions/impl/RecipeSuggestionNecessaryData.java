package eu.dietwise.services.v1.suggestions.impl;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.Recommendation;

/**
 * The data a recipe assessment reads once and uses for every ingredient. The {@code recommendations} are keyed by the
 * normalized name of their component for scoring, the {@code componentsByRecommendation} by their Recommendation, the
 * language-independent identity the Trigger Ingredients and the Rules refer to.
 */
record RecipeSuggestionNecessaryData(
		PersonalInfo personalInfo,
		Map<String, RoleOrTechnique> roles,
		Map<String, TriggerIngredient> triggerIngredients,
		Map<String, AlternativeIngredient> alternatives,
		Map<String, RecommendationComponent> recommendations,
		Map<Recommendation, RecommendationComponent> componentsByRecommendation
) {
	RecipeSuggestionNecessaryData(
			PersonalInfo personalInfo,
			Map<String, RoleOrTechnique> roles,
			Map<String, TriggerIngredient> triggerIngredients,
			Map<String, AlternativeIngredient> alternatives,
			Map<String, RecommendationComponent> recommendations
	) {
		this(personalInfo, roles, triggerIngredients, alternatives, recommendations,
				recommendations.values().stream().collect(toMap(RecommendationComponent::getRecommendation, identity())));
	}

	/** The components of the given Recommendations; a Recommendation without a component is skipped. */
	Set<RecommendationComponent> componentsOf(Collection<Recommendation> recommendations) {
		return recommendations.stream().map(componentsByRecommendation::get).filter(Objects::nonNull).collect(Collectors.toSet());
	}
}
