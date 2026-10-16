package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * One Trigger Ingredient row of the grid of components for scoring: its id, effective English name (published master
 * overlaid by any Staged Change), whether a published master row exists behind it, the Working Copy version a subsequent
 * name/explanation edit must be based on ({@code 0} when there is no Staged Change yet), the completeness of its
 * translations for each non-English language, and its links to the grid's Recommendation columns.
 * <p>
 * The links are given as sets, all restricted to the grid's columns: {@code linkedRecommendationIds} are the
 * Recommendations linked in published master, and {@code stagedRecommendationIds} are the Recommendations whose link
 * carries a pending change in the Working Copy. A column's effective presence is the master link toggled by a staged
 * change: a staged id absent from master is a staged addition, a staged id present in master is a staged removal.
 * {@code ruleRecommendationIds} are the Recommendations of the Trigger Ingredient's active Rules (published master
 * overlaid by the Working Copy); recipe assessment always counts the Recommendation of the Rule it selects as the primary
 * component of the ingredient, so these links cannot be removed.
 */
public record StagedTriggerIngredient(
		UUID id,
		String name,
		boolean published,
		long version,
		Map<RecipeLanguage, TranslationState> translations,
		Set<UUID> linkedRecommendationIds,
		Set<UUID> stagedRecommendationIds,
		Set<UUID> ruleRecommendationIds
) {
	public StagedTriggerIngredient {
		translations = Map.copyOf(translations);
		linkedRecommendationIds = Set.copyOf(linkedRecommendationIds);
		stagedRecommendationIds = Set.copyOf(stagedRecommendationIds);
		ruleRecommendationIds = Set.copyOf(ruleRecommendationIds);
	}
}
