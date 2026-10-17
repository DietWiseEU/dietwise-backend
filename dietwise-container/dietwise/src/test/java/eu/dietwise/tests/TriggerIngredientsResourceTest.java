package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.services.v1.BackofficeTriggerIngredientsService;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedTriggerIngredient;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.TypeOfRecommendation;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class TriggerIngredientsResourceTest extends AdminResourceTest {
	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("c3c3c3c3-0000-4000-8000-000000000003");
	private static final UUID RECOMMENDATION_ID = UUID.fromString("b2b2b2b2-0000-4000-8000-000000000002");

	@InjectMock
	BackofficeTriggerIngredientsService service;

	@Test
	void recommendationGridReturnsTheGridOfTheService() {
		when(service.recommendationGrid(any())).thenReturn(Uni.createFrom().item(new TriggerIngredientRecommendationGrid(
				List.of(new RecommendationColumn(RECOMMENDATION_ID, "red meat", TypeOfRecommendation.LIMITED)),
				List.of(new StagedTriggerIngredient(TRIGGER_INGREDIENT_ID, "Beef", false, 2,
						Map.of(RecipeLanguage.NL, TranslationState.MISSING), Set.of(), Set.of(RECOMMENDATION_ID), Set.of(RECOMMENDATION_ID))))));

		given()
				.when()
				.get("/api/v1/trigger-ingredients/recommendations")
				.then()
				.statusCode(200)
				.body("columns[0].id", equalTo(RECOMMENDATION_ID.toString()))
				.body("columns[0].componentForScoring", equalTo("red meat"))
				.body("columns[0].typeOfRecommendation", equalTo("LIMITED"))
				.body("triggerIngredients[0].id", equalTo(TRIGGER_INGREDIENT_ID.toString()))
				.body("triggerIngredients[0].name", equalTo("Beef"))
				.body("triggerIngredients[0].published", equalTo(false))
				.body("triggerIngredients[0].translations.NL", equalTo("MISSING"))
				.body("triggerIngredients[0].linkedRecommendationIds", empty())
				.body("triggerIngredients[0].stagedRecommendationIds", contains(RECOMMENDATION_ID.toString()))
				.body("triggerIngredients[0].ruleRecommendationIds", contains(RECOMMENDATION_ID.toString()));

		verify(service).recommendationGrid(authenticatedUser());
	}

	@Test
	void toggleRecommendationStagesTheLink() {
		when(service.toggleRecommendation(any(), any(), any(), anyBoolean())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"present\": false}")
				.when()
				.put("/api/v1/trigger-ingredients/{id}/recommendations/{recommendationId}", TRIGGER_INGREDIENT_ID, RECOMMENDATION_ID)
				.then()
				.statusCode(204);

		verify(service).toggleRecommendation(authenticatedUser(), eq(TRIGGER_INGREDIENT_ID), eq(RECOMMENDATION_ID), eq(false));
	}
}
