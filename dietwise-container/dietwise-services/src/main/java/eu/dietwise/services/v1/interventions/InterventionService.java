package eu.dietwise.services.v1.interventions;

import java.util.Optional;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.UiLanguage;
import io.smallrye.mutiny.Uni;

public interface InterventionService {
	/**
	 * Assign an Intervention, picked uniformly among the active ones, to an assessment that produced the given
	 * Suggestions, and record the Intervention Assignment. The text is in {@code uiLang}, falling back to English when
	 * the Intervention has no text in it.
	 *
	 * @return The assigned Intervention, empty if there is no {@code uiLang}, no Suggestion, or no active Intervention
	 */
	Uni<Optional<AssignedIntervention>> assign(
			ReactivePersistenceTxContext tx,
			HasUserId hasUserId,
			String applicationId,
			UiLanguage uiLang,
			String recipeUrl,
			HasSuggestionTemplateIds suggestions
	);
}
