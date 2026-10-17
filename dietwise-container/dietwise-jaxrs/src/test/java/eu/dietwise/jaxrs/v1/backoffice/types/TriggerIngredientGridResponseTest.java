package eu.dietwise.jaxrs.v1.backoffice.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedTriggerIngredient;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.TypeOfRecommendation;
import org.junit.jupiter.api.Test;

class TriggerIngredientGridResponseTest {
	private static final UUID REC_RED_MEAT_ID = UUID.fromString("b1b2b3b4-0012-4f5a-8b9c-0d1e2f3a0012");
	private static final UUID REC_SODIUM_ID = UUID.fromString("b1b2b3b4-0013-4f5a-8b9c-0d1e2f3a0013");
	private static final UUID TI_BEEF_ID = UUID.fromString("d1d2d3d4-0001-4f5a-8b9c-0d1e2f3a0001");

	@Test
	void fromCarriesColumnsAndEveryRowFieldIncludingTheRecommendationsOfTheRules() {
		var grid = new TriggerIngredientRecommendationGrid(
				List.of(new RecommendationColumn(REC_RED_MEAT_ID, "red meat", TypeOfRecommendation.LIMITED)),
				List.of(new StagedTriggerIngredient(TI_BEEF_ID, "Beef", true, 3L,
						Map.of(RecipeLanguage.EL, TranslationState.PRESENT),
						Set.of(REC_RED_MEAT_ID), Set.of(REC_SODIUM_ID), Set.of(REC_RED_MEAT_ID))));

		var response = TriggerIngredientGridResponse.from(grid);

		assertThat(response.columns()).containsExactly(new RecommendationColumnResponse(REC_RED_MEAT_ID.toString(), "red meat", "LIMITED"));
		assertThat(response.triggerIngredients()).containsExactly(new TriggerIngredientGridResponse.TriggerIngredient(
				TI_BEEF_ID.toString(), "Beef", true, 3L, Map.of("EL", "PRESENT"),
				List.of(REC_RED_MEAT_ID.toString()), List.of(REC_SODIUM_ID.toString()), List.of(REC_RED_MEAT_ID.toString())));
	}
}
