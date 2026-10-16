package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TriggerIngredientComponentForScoringWcEntityId implements Serializable {
	private UUID triggerIngredientId;
	private UUID recommendationId;

	public TriggerIngredientComponentForScoringWcEntityId() {
	}

	public TriggerIngredientComponentForScoringWcEntityId(UUID triggerIngredientId, UUID recommendationId) {
		this.triggerIngredientId = triggerIngredientId;
		this.recommendationId = recommendationId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TriggerIngredientComponentForScoringWcEntityId that)) return false;
		return Objects.equals(triggerIngredientId, that.triggerIngredientId)
				&& Objects.equals(recommendationId, that.recommendationId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(triggerIngredientId, recommendationId);
	}
}
