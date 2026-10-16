package eu.dietwise.jaxrs.v1;

import static eu.dietwise.jaxrs.v1.GridResponses.toIdStrings;
import static eu.dietwise.jaxrs.v1.GridResponses.toStateNames;

import java.util.List;
import java.util.Map;

import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import eu.dietwise.services.v1.types.StagedAlternativeIngredient;

/**
 * The substitution-value grid as returned to the backoffice: the Recommendation {@code columns} (in display
 * order) and one {@code ingredients} row per Alternative Ingredient (sorted by name). Each row carries its effective
 * name, per-language translation completeness (language name to state name) and its links to the columns as two id
 * lists — the published-master links and the links carrying a pending change in the Working Copy.
 */
public record AlternativeIngredientGridResponse(
		List<RecommendationColumnResponse> columns,
		List<Ingredient> ingredients
) {
	public AlternativeIngredientGridResponse {
		columns = List.copyOf(columns);
		ingredients = List.copyOf(ingredients);
	}

	/**
	 * One grid row: an Alternative Ingredient's id, effective name, whether a published master row exists, the Working
	 * Copy version a subsequent name/explanation edit must be based on, its per-language translation completeness, and its
	 * master and staged links to the grid columns.
	 */
	public record Ingredient(
			String id,
			String name,
			boolean published,
			long version,
			Map<String, String> translations,
			List<String> linkedRecommendationIds,
			List<String> stagedRecommendationIds
	) {
		public Ingredient {
			translations = Map.copyOf(translations);
			linkedRecommendationIds = List.copyOf(linkedRecommendationIds);
			stagedRecommendationIds = List.copyOf(stagedRecommendationIds);
		}

		static Ingredient from(StagedAlternativeIngredient ingredient) {
			return new Ingredient(
					ingredient.id().toString(),
					ingredient.name(),
					ingredient.published(),
					ingredient.version(),
					toStateNames(ingredient.translations()),
					toIdStrings(ingredient.linkedRecommendationIds()),
					toIdStrings(ingredient.stagedRecommendationIds()));
		}
	}

	public static AlternativeIngredientGridResponse from(AlternativeIngredientRecommendationGrid grid) {
		return new AlternativeIngredientGridResponse(
				grid.columns().stream().map(RecommendationColumnResponse::from).toList(),
				grid.ingredients().stream().map(Ingredient::from).toList());
	}
}
