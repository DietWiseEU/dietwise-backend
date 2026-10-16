package eu.dietwise.services.model.suggestions;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.types.Recommendation;
import org.immutables.value.Value;

@Value.Immutable
public interface TriggerIngredient extends HasTriggerIngredientId, Serializable {
	String getName();

	Optional<String> getExplanationForLlm();

	/**
	 * The components for scoring a recipe ingredient matched to this Trigger Ingredient contributes to the score of the
	 * recipe, identified by their Recommendations.
	 */
	Set<Recommendation> getComponentsForScoring();
}
