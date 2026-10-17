package eu.dietwise.jaxrs.v1.backoffice.types;

import static eu.dietwise.jaxrs.v1.backoffice.types.GridResponses.toIdStrings;
import static eu.dietwise.jaxrs.v1.backoffice.types.GridResponses.toStateNames;

import java.util.List;
import java.util.Map;

import eu.dietwise.services.v1.types.StagedTriggerIngredient;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;

/**
 * The grid of the components for scoring of the Trigger Ingredients as returned to the backoffice: the Recommendation
 * {@code columns} (in display order) and one {@code triggerIngredients} row per Trigger Ingredient (sorted by name).
 * Each row carries its effective name, per-language translation completeness (language name to state name), its links
 * to the columns as two id lists — the published-master links and the links carrying a pending change in the Working
 * Copy — and the ids of the Recommendations of its active Rules, whose links cannot be removed.
 */
public record TriggerIngredientGridResponse(
		List<RecommendationColumnResponse> columns,
		List<TriggerIngredient> triggerIngredients
) {
	public TriggerIngredientGridResponse {
		columns = List.copyOf(columns);
		triggerIngredients = List.copyOf(triggerIngredients);
	}

	/**
	 * One grid row: a Trigger Ingredient's id, effective name, whether a published master row exists, the Working Copy
	 * version a subsequent name/explanation edit must be based on, its per-language translation completeness, its master
	 * and staged links to the grid columns, and the Recommendations of its active Rules.
	 */
	public record TriggerIngredient(
			String id,
			String name,
			boolean published,
			long version,
			Map<String, String> translations,
			List<String> linkedRecommendationIds,
			List<String> stagedRecommendationIds,
			List<String> ruleRecommendationIds
	) {
		public TriggerIngredient {
			translations = Map.copyOf(translations);
			linkedRecommendationIds = List.copyOf(linkedRecommendationIds);
			stagedRecommendationIds = List.copyOf(stagedRecommendationIds);
			ruleRecommendationIds = List.copyOf(ruleRecommendationIds);
		}

		static TriggerIngredient from(StagedTriggerIngredient triggerIngredient) {
			return new TriggerIngredient(
					triggerIngredient.id().toString(),
					triggerIngredient.name(),
					triggerIngredient.published(),
					triggerIngredient.version(),
					toStateNames(triggerIngredient.translations()),
					toIdStrings(triggerIngredient.linkedRecommendationIds()),
					toIdStrings(triggerIngredient.stagedRecommendationIds()),
					toIdStrings(triggerIngredient.ruleRecommendationIds()));
		}
	}

	public static TriggerIngredientGridResponse from(TriggerIngredientRecommendationGrid grid) {
		return new TriggerIngredientGridResponse(
				grid.columns().stream().map(RecommendationColumnResponse::from).toList(),
				grid.triggerIngredients().stream().map(TriggerIngredient::from).toList());
	}
}
