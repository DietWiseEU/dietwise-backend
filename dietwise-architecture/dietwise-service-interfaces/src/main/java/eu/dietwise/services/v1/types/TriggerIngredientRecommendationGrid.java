package eu.dietwise.services.v1.types;

import java.util.List;

/**
 * The whole grid of the components for scoring of the Trigger Ingredients: the Recommendation {@code columns} (in
 * display order: the ENCOURAGED ones, then the LIMITED ones) and one {@code triggerIngredients} row per Trigger
 * Ingredient (sorted by name), each carrying its links to those columns. A cell is the crossing of a row and a column;
 * its effective presence and pending-change state are derived from the row's master and staged link sets.
 */
public record TriggerIngredientRecommendationGrid(
		List<RecommendationColumn> columns,
		List<StagedTriggerIngredient> triggerIngredients
) {
	public TriggerIngredientRecommendationGrid {
		columns = List.copyOf(columns);
		triggerIngredients = List.copyOf(triggerIngredients);
	}
}
