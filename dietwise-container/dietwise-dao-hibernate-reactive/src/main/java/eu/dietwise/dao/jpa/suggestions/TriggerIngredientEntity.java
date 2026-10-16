package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.FetchType.LAZY;

import java.util.Set;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;

@Entity
@Table(name = "DW_TRIGGER_INGREDIENT")
public class TriggerIngredientEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	/**
	 * The components for scoring, from the available ones under {@link RecommendationEntity#getComponentForScoring},
	 * that a recipe ingredient matched to this Trigger Ingredient contributes to the score of the recipe. Curated by the
	 * scientific partners instead of having the LLM unreliably determine it over and over again.
	 */
	@ManyToMany
	@JoinTable(name = "DW_TRIGGER_INGREDIENT_COMPONENTS_FOR_SCORING",
			joinColumns = @JoinColumn(name = "trigger_ingredient_id"),
			inverseJoinColumns = @JoinColumn(name = "recommendation_id")
	)
	private Set<RecommendationEntity> componentsForScoring;

	@OneToMany(mappedBy = "triggerIngredient", fetch = LAZY)
	private Set<TriggerIngredientTranslationEntity> translations;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getExplanationForLlm() {
		return explanationForLlm;
	}

	public void setExplanationForLlm(String explanationForLlm) {
		this.explanationForLlm = explanationForLlm;
	}

	public Set<RecommendationEntity> getComponentsForScoring() {
		return componentsForScoring;
	}

	public void setComponentsForScoring(Set<RecommendationEntity> componentsForScoring) {
		this.componentsForScoring = componentsForScoring;
	}

	public Set<TriggerIngredientTranslationEntity> getTranslations() {
		return translations;
	}

	public void setTranslations(Set<TriggerIngredientTranslationEntity> translations) {
		this.translations = translations;
	}
}
