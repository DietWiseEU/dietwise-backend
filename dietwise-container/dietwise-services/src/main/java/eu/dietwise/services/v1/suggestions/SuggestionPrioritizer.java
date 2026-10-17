package eu.dietwise.services.v1.suggestions;

import java.util.List;
import java.util.Map;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.IngredientId;
import io.smallrye.mutiny.Uni;

public interface SuggestionPrioritizer {
	/**
	 * Orders the Suggestions by the weight of the primary component of the ingredient each one applies to, highest first.
	 * The Suggestions of an ingredient come from the Rule of its primary component.
	 */
	Uni<List<Suggestion>> prioritizeSuggestions(
			ReactivePersistenceContext em,
			PersonalInfo personalInfo,
			List<Suggestion> suggestions,
			Map<IngredientId, RecommendationComponent> primaryComponents
	);
}
