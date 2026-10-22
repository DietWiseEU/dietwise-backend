package eu.dietwise.services.model.interventions;

import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.SuggestionTemplateId;
import org.immutables.value.Value;

/**
 * One accept/reject transition a user made on one Suggestion of an Intervention Assignment.
 */
@Value.Immutable
public interface SuggestionEvent {
	UUID getAssessmentId();

	SuggestionTemplateId getSuggestionTemplateId();

	SuggestionStatsTransition getAction();

	LocalDateTime getCreatedAt();
}
