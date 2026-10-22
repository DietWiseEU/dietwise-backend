package eu.dietwise.dao.jpa.interventions;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class InterventionAssignmentSuggestionId implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID interventionAssignmentAssessmentId;

	private UUID suggestionTemplateId;

	public UUID getInterventionAssignmentAssessmentId() {
		return interventionAssignmentAssessmentId;
	}

	public void setInterventionAssignmentAssessmentId(UUID interventionAssignmentAssessmentId) {
		this.interventionAssignmentAssessmentId = interventionAssignmentAssessmentId;
	}

	public UUID getSuggestionTemplateId() {
		return suggestionTemplateId;
	}

	public void setSuggestionTemplateId(UUID suggestionTemplateId) {
		this.suggestionTemplateId = suggestionTemplateId;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof InterventionAssignmentSuggestionId other)) {
			return false;
		}
		return Objects.equals(interventionAssignmentAssessmentId, other.interventionAssignmentAssessmentId) && Objects.equals(suggestionTemplateId, other.suggestionTemplateId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(interventionAssignmentAssessmentId, suggestionTemplateId);
	}
}
