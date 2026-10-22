package eu.dietwise.services.model.interventions;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.SuggestionTemplateId;
import org.immutables.value.Value;

/**
 * The Intervention the backend picked for one assessment, with the Suggestions shown under it.
 */
@Value.Immutable
public interface InterventionAssignment {
	UUID getAssessmentId();

	UUID getUserId();

	String getApplicationId();

	int getInterventionId();

	/** The language of the Intervention text sent to the client. */
	RecipeLanguage getUiLang();

	String getRecipeUrl();

	LocalDateTime getCreatedAt();

	Set<SuggestionTemplateId> getSuggestionTemplateIds();
}
