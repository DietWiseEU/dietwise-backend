package eu.dietwise.dao.jpa.interventions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "DW_INTERVENTION_ASSIGNMENT_SUGGESTIONS")
@IdClass(InterventionAssignmentSuggestionId.class)
public class InterventionAssignmentSuggestionEntity {
	@Id
	@Column(name = "intervention_assignment_assessment_id")
	private UUID interventionAssignmentAssessmentId;

	@Id
	@Column(name = "suggestion_template_id")
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
}
