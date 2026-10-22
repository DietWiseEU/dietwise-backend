package eu.dietwise.v1.model;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.SuggestionTemplateId;
import org.immutables.value.Value;

@Value.Immutable
public interface StatisticsParam {
	@NotNull
	SuggestionTemplateId getSuggestionId();

	/**
	 * The assessment that showed the suggestion; absent for clients that show no Intervention.
	 */
	@Nullable
	UUID getAssessmentId();
}
