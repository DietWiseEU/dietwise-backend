package eu.dietwise.dao.jpa.interventions;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.SuggestionStatsTransition;

/**
 * Suggestion Events: an append-only log of the accept/reject transitions a user made on the Suggestions of an Intervention Assignment.
 */
@Entity
@Table(name = "DW_SUGGESTION_EVENT")
public class SuggestionEventEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "intervention_assignment_assessment_id")
	private UUID interventionAssignmentAssessmentId;

	@Column(name = "suggestion_template_id")
	private UUID suggestionTemplateId;

	@Enumerated(STRING)
	@Column(name = "action")
	private SuggestionStatsTransition action;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

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

	public SuggestionStatsTransition getAction() {
		return action;
	}

	public void setAction(SuggestionStatsTransition action) {
		this.action = action;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
