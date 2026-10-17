package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.services.v1.BackofficeRecommendationsService;
import eu.dietwise.services.v1.types.StagedRecommendation;
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
class RecommendationsResourceTest extends AdminResourceTest {
	private static final UUID RECOMMENDATION_ID = UUID.fromString("b2b2b2b2-0000-4000-8000-000000000002");

	@InjectMock
	BackofficeRecommendationsService service;

	@Test
	void listRecommendationsReturnsTheRecommendationsOfTheService() {
		when(service.listRecommendations(any())).thenReturn(Uni.createFrom().item(List.of(new StagedRecommendation(
				RECOMMENDATION_ID, "Eat more legumes", "legumes", TypeOfRecommendation.ENCOURAGED, "Legumes are pulses", true,
				"More legumes", false, 4, Map.of(RecipeLanguage.LT, TranslationState.STAGED)))));

		given()
				.when()
				.get("/api/v1/recommendations")
				.then()
				.statusCode(200)
				.body("[0].id", equalTo(RECOMMENDATION_ID.toString()))
				.body("[0].name", equalTo("Eat more legumes"))
				.body("[0].typeOfRecommendation", equalTo("ENCOURAGED"))
				.body("[0].explanationChanged", equalTo(true))
				.body("[0].humanFriendlyDisplay", equalTo("More legumes"))
				.body("[0].version", equalTo(4))
				.body("[0].translations.LT", equalTo("STAGED"));

		verify(service).listRecommendations(authenticatedUser());
	}

	@Test
	void stageMasterStagesTheEnglishTextsAndReturnsTheNewVersion() {
		when(service.stageMaster(any(), any(), any(), any(), anyLong())).thenReturn(Uni.createFrom().item(4L));

		given()
				.contentType(ContentType.JSON)
				.body("{\"explanationForLlm\": \"Legumes are pulses\", \"humanFriendlyDisplay\": \"More legumes\", \"baseVersion\": 3}")
				.when()
				.put("/api/v1/recommendations/{id}/master", RECOMMENDATION_ID)
				.then()
				.statusCode(200)
				.body("version", equalTo(4));

		verify(service).stageMaster(authenticatedUser(), eq(RECOMMENDATION_ID), eq("Legumes are pulses"), eq("More legumes"), eq(3L));
	}

	@Test
	void revertMasterRevertsTheEnglishTexts() {
		when(service.revertMaster(any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 3)
				.when()
				.delete("/api/v1/recommendations/{id}/master", RECOMMENDATION_ID)
				.then()
				.statusCode(204);

		verify(service).revertMaster(authenticatedUser(), eq(RECOMMENDATION_ID), eq(3L));
	}

	@Test
	void translationsForEditReturnsTheTranslationsByLanguage() {
		when(service.translationsForEdit(any(), any())).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.EL, new RecommendationTranslationDetails("Περισσότερα όσπρια", "όσπρια", "Τα όσπρια", "Όσπρια", 2))));

		given()
				.when()
				.get("/api/v1/recommendations/{id}/translations", RECOMMENDATION_ID)
				.then()
				.statusCode(200)
				.body("EL.name", equalTo("Περισσότερα όσπρια"))
				.body("EL.componentForScoring", equalTo("όσπρια"))
				.body("EL.explanationForLlm", equalTo("Τα όσπρια"))
				.body("EL.humanFriendlyDisplay", equalTo("Όσπρια"))
				.body("EL.version", equalTo(2));

		verify(service).translationsForEdit(authenticatedUser(), eq(RECOMMENDATION_ID));
	}

	@Test
	void stageTranslationStagesTheTranslationOfTheLanguage() {
		when(service.stageTranslation(any(), any(), any(), any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("""
						{"name": "Meer peulvruchten", "componentForScoring": "peulvruchten", "explanationForLlm": "Peulvruchten zijn", "humanFriendlyDisplay": "Peulvruchten", "baseVersion": 2}
						""")
				.when()
				.put("/api/v1/recommendations/{id}/translations/{lang}", RECOMMENDATION_ID, "NL")
				.then()
				.statusCode(204);

		verify(service).stageTranslation(authenticatedUser(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.NL), eq("Meer peulvruchten"),
				eq("peulvruchten"), eq("Peulvruchten zijn"), eq("Peulvruchten"), eq(2L));
	}

	@Test
	void revertTranslationRevertsTheTranslationOfTheLanguage() {
		when(service.revertTranslation(any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 2)
				.when()
				.delete("/api/v1/recommendations/{id}/translations/{lang}", RECOMMENDATION_ID, "NL")
				.then()
				.statusCode(204);

		verify(service).revertTranslation(authenticatedUser(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.NL), eq(2L));
	}
}
