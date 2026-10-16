package eu.dietwise.services.v1.suggestions;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.IngredientId;

/**
 * The Suggestions for a recipe, the components for scoring each of its ingredients contributes to its score, and the
 * primary component of each ingredient a Rule was selected for: the component of that Rule's Recommendation, also
 * contained in the ingredient's components.
 */
public record MakeSuggestionsResult(
		SuggestionsRecipeAssessmentMessage message,
		Map<IngredientId, Set<RecommendationComponent>> recommendations,
		Map<IngredientId, RecommendationComponent> primaryComponents,
		PersonalInfo personalInfo
) {
	public MakeSuggestionsResult {
		recommendations = Map.copyOf(recommendations.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> Set.copyOf(entry.getValue())
				)));
		primaryComponents = Map.copyOf(primaryComponents);
	}
}
