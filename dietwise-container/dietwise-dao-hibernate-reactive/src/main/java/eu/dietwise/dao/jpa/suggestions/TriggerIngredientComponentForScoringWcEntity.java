package eu.dietwise.dao.jpa.suggestions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The Working Copy mirror of the {@code DW_TRIGGER_INGREDIENT_COMPONENTS_FOR_SCORING} join: a staged change to
 * whether one Trigger Ingredient contributes one Recommendation's component for scoring. Sparse — a row exists only
 * while the effective link differs from published master. {@link #present} carries the absolute staged presence (true =
 * staged addition, false = staged removal) rather than a flip, so the staged intent survives a later change to master.
 */
@Entity
@IdClass(TriggerIngredientComponentForScoringWcEntityId.class)
@Table(name = "DW_TRIGGER_INGREDIENT_COMPONENTS_FOR_SCORING_WC")
public class TriggerIngredientComponentForScoringWcEntity {
	@Id
	@Column(name = "trigger_ingredient_id")
	private UUID triggerIngredientId;

	@Id
	@Column(name = "recommendation_id")
	private UUID recommendationId;

	@Column(name = "present")
	private boolean present;

	public UUID getTriggerIngredientId() {
		return triggerIngredientId;
	}

	public void setTriggerIngredientId(UUID triggerIngredientId) {
		this.triggerIngredientId = triggerIngredientId;
	}

	public UUID getRecommendationId() {
		return recommendationId;
	}

	public void setRecommendationId(UUID recommendationId) {
		this.recommendationId = recommendationId;
	}

	public boolean isPresent() {
		return present;
	}

	public void setPresent(boolean present) {
		this.present = present;
	}
}
