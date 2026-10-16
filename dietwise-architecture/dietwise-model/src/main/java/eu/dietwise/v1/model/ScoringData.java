package eu.dietwise.v1.model;

import java.util.Map;
import java.util.Set;

import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;
import org.immutables.value.Value;

@Value.Immutable
public interface ScoringData {
	int getTotalNumberOfRecomendations();

	Map<RecommendationComponentName, RecommendationSpecialWeight> getRecommendationWeights();

	Map<RecommendationComponentName, String> getHumanFriendlyDisplays();

	Map<IngredientId, Set<RecommendationComponentName>> getRecommendationsPerIngredient();

	/**
	 * The primary component of each ingredient a Rule was selected for: the component of that Rule's Recommendation,
	 * also contained in the components of the ingredient in {@link #getRecommendationsPerIngredient()}. An ingredient
	 * without a selected Rule has no entry.
	 */
	Map<IngredientId, RecommendationComponentName> getPrimaryComponentPerIngredient();
}
