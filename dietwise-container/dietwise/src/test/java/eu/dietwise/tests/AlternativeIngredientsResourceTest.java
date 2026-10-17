package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
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

import eu.dietwise.services.v1.BackofficeAlternativeIngredientsService;
import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedAlternativeIngredient;
import eu.dietwise.services.v1.types.TranslationState;
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
class AlternativeIngredientsResourceTest extends AdminResourceTest {
	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");
	private static final UUID RECOMMENDATION_ID = UUID.fromString("b2b2b2b2-0000-4000-8000-000000000002");

	@InjectMock
	BackofficeAlternativeIngredientsService service;

	@Test
	void recommendationGridReturnsTheGridOfTheService() {
		when(service.recommendationGrid(any())).thenReturn(Uni.createFrom().item(new AlternativeIngredientRecommendationGrid(
				List.of(new RecommendationColumn(RECOMMENDATION_ID, "legumes", TypeOfRecommendation.ENCOURAGED)),
				List.of(new StagedAlternativeIngredient(ALTERNATIVE_INGREDIENT_ID, "Lentils", true, 3,
						Map.of(RecipeLanguage.EL, TranslationState.PARTIAL), Set.of(RECOMMENDATION_ID), Set.of())))));

		given()
				.when()
				.get("/api/v1/alternative-ingredients/recommendations")
				.then()
				.statusCode(200)
				.body("columns[0].id", equalTo(RECOMMENDATION_ID.toString()))
				.body("columns[0].componentForScoring", equalTo("legumes"))
				.body("columns[0].typeOfRecommendation", equalTo("ENCOURAGED"))
				.body("ingredients[0].id", equalTo(ALTERNATIVE_INGREDIENT_ID.toString()))
				.body("ingredients[0].name", equalTo("Lentils"))
				.body("ingredients[0].version", equalTo(3))
				.body("ingredients[0].translations.EL", equalTo("PARTIAL"))
				.body("ingredients[0].linkedRecommendationIds", contains(RECOMMENDATION_ID.toString()));

		verify(service).recommendationGrid(authenticatedUser());
	}

	@Test
	void toggleRecommendationStagesTheLink() {
		when(service.toggleRecommendation(any(), any(), any(), anyBoolean())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"present\": true}")
				.when()
				.put("/api/v1/alternative-ingredients/{id}/recommendations/{recommendationId}", ALTERNATIVE_INGREDIENT_ID, RECOMMENDATION_ID)
				.then()
				.statusCode(204);

		verify(service).toggleRecommendation(authenticatedUser(), eq(ALTERNATIVE_INGREDIENT_ID), eq(RECOMMENDATION_ID), eq(true));
	}

	@Test
	void discardAlternativeIngredientDiscardsIt() {
		when(service.discardAlternativeIngredient(any(), any())).thenReturn(VOID);

		given()
				.when()
				.delete("/api/v1/alternative-ingredients/{id}", ALTERNATIVE_INGREDIENT_ID)
				.then()
				.statusCode(204);

		verify(service).discardAlternativeIngredient(authenticatedUser(), eq(ALTERNATIVE_INGREDIENT_ID));
	}
}
