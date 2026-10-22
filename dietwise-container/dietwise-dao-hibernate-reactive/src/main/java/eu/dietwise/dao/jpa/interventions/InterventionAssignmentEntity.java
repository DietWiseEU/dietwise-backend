package eu.dietwise.dao.jpa.interventions;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecipeLanguage;

@Entity
@Table(name = "DW_INTERVENTION_ASSIGNMENT")
public class InterventionAssignmentEntity {
	@Id
	@Column(name = "assessment_id")
	private UUID assessmentId;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "application_id")
	private String applicationId;

	@Column(name = "intervention_id")
	private Integer interventionId;

	@Enumerated(STRING)
	@Column(name = "ui_lang")
	private RecipeLanguage uiLang;

	@Column(name = "recipe_url")
	private String recipeUrl;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public UUID getAssessmentId() {
		return assessmentId;
	}

	public void setAssessmentId(UUID assessmentId) {
		this.assessmentId = assessmentId;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public Integer getInterventionId() {
		return interventionId;
	}

	public void setInterventionId(Integer interventionId) {
		this.interventionId = interventionId;
	}

	public RecipeLanguage getUiLang() {
		return uiLang;
	}

	public void setUiLang(RecipeLanguage uiLang) {
		this.uiLang = uiLang;
	}

	public String getRecipeUrl() {
		return recipeUrl;
	}

	public void setRecipeUrl(String recipeUrl) {
		this.recipeUrl = recipeUrl;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
