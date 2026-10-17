package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeReferenceDataService;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.v1.types.RecipeLanguage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The endpoints of the {@code RulesResource} that are served by the {@link BackofficeReferenceDataService}. The three
 * kinds of reference data (Trigger Ingredients, Roles or Techniques and Alternative Ingredients) share the same
 * operations, each through its own method of the service, so each test runs for every kind.
 */
@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class RulesResourceReferenceDataTest extends AdminResourceTest {
	private static final UUID REFERENCE_ID = UUID.fromString("a7a7a7a7-0000-4000-8000-000000000007");

	@InjectMock
	BackofficeReferenceDataService service;

	@FunctionalInterface
	interface Creation {
		Uni<ReferenceOption> apply(BackofficeReferenceDataService service, User user, String name);
	}

	@FunctionalInterface
	interface ReferenceForEdit {
		Uni<ReferenceDetails> apply(BackofficeReferenceDataService service, User user, UUID id);
	}

	@FunctionalInterface
	interface Edit {
		Uni<Void> apply(BackofficeReferenceDataService service, User user, UUID id, String name, String explanationForLlm, long baseVersion);
	}

	@FunctionalInterface
	interface Revert {
		Uni<Void> apply(BackofficeReferenceDataService service, User user, UUID id, long baseVersion);
	}

	@FunctionalInterface
	interface TranslationsForEdit {
		Uni<Map<RecipeLanguage, ReferenceDetails>> apply(BackofficeReferenceDataService service, User user, UUID id);
	}

	@FunctionalInterface
	interface TranslationStage {
		Uni<Void> apply(BackofficeReferenceDataService service, User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);
	}

	@FunctionalInterface
	interface TranslationRevert {
		Uni<Void> apply(BackofficeReferenceDataService service, User user, UUID id, RecipeLanguage lang, long baseVersion);
	}

	static Stream<Arguments> creations() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (Creation) BackofficeReferenceDataService::createTriggerIngredient),
				Arguments.of("roles-or-techniques", (Creation) BackofficeReferenceDataService::createRoleOrTechnique),
				Arguments.of("alternative-ingredients", (Creation) BackofficeReferenceDataService::createAlternativeIngredient)
		);
	}

	static Stream<Arguments> referencesForEdit() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (ReferenceForEdit) BackofficeReferenceDataService::triggerIngredientForEdit),
				Arguments.of("roles-or-techniques", (ReferenceForEdit) BackofficeReferenceDataService::roleOrTechniqueForEdit)
		);
	}

	static Stream<Arguments> edits() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (Edit) BackofficeReferenceDataService::editTriggerIngredient),
				Arguments.of("roles-or-techniques", (Edit) BackofficeReferenceDataService::editRoleOrTechnique),
				Arguments.of("alternative-ingredients", (Edit) BackofficeReferenceDataService::editAlternativeIngredient)
		);
	}

	static Stream<Arguments> reverts() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (Revert) BackofficeReferenceDataService::revertTriggerIngredient),
				Arguments.of("roles-or-techniques", (Revert) BackofficeReferenceDataService::revertRoleOrTechnique),
				Arguments.of("alternative-ingredients", (Revert) BackofficeReferenceDataService::revertAlternativeIngredient)
		);
	}

	static Stream<Arguments> translationsForEdit() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (TranslationsForEdit) BackofficeReferenceDataService::triggerIngredientTranslationsForEdit),
				Arguments.of("roles-or-techniques", (TranslationsForEdit) BackofficeReferenceDataService::roleOrTechniqueTranslationsForEdit),
				Arguments.of("alternative-ingredients", (TranslationsForEdit) BackofficeReferenceDataService::alternativeIngredientTranslationsForEdit)
		);
	}

	static Stream<Arguments> translationStages() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (TranslationStage) BackofficeReferenceDataService::stageTriggerIngredientTranslation),
				Arguments.of("roles-or-techniques", (TranslationStage) BackofficeReferenceDataService::stageRoleOrTechniqueTranslation),
				Arguments.of("alternative-ingredients", (TranslationStage) BackofficeReferenceDataService::stageAlternativeIngredientTranslation)
		);
	}

	static Stream<Arguments> translationReverts() {
		return Stream.of(
				Arguments.of("trigger-ingredients", (TranslationRevert) BackofficeReferenceDataService::revertTriggerIngredientTranslation),
				Arguments.of("roles-or-techniques", (TranslationRevert) BackofficeReferenceDataService::revertRoleOrTechniqueTranslation),
				Arguments.of("alternative-ingredients", (TranslationRevert) BackofficeReferenceDataService::revertAlternativeIngredientTranslation)
		);
	}

	@ParameterizedTest
	@MethodSource("creations")
	void createStagesTheReferenceAndReturnsItsOption(String kind, Creation creation) {
		when(creation.apply(service, any(), any())).thenReturn(Uni.createFrom().item(new ReferenceOption(REFERENCE_ID, "Chickpeas")));

		given()
				.contentType(ContentType.JSON)
				.body("{\"name\": \"Chickpeas\"}")
				.when()
				.post("/api/v1/rules/" + kind)
				.then()
				.statusCode(200)
				.body("id", equalTo(REFERENCE_ID.toString()))
				.body("name", equalTo("Chickpeas"));

		creation.apply(verify(service), authenticatedUser(), eq("Chickpeas"));
	}

	@ParameterizedTest
	@MethodSource("referencesForEdit")
	void forEditReturnsTheDetailsOfTheReference(String kind, ReferenceForEdit forEdit) {
		when(forEdit.apply(service, any(), any())).thenReturn(Uni.createFrom().item(new ReferenceDetails("Chickpeas", "Pulses", 3, true)));

		given()
				.when()
				.get("/api/v1/rules/{kind}/{id}", kind, REFERENCE_ID)
				.then()
				.statusCode(200)
				.body("name", equalTo("Chickpeas"))
				.body("explanationForLlm", equalTo("Pulses"))
				.body("version", equalTo(3))
				.body("published", equalTo(true));

		forEdit.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID));
	}

	@Test
	void alternativeIngredientForEditReturnsTheDetailsOfTheAlternativeIngredient() {
		when(service.alternativeIngredientForEdit(any(), any())).thenReturn(Uni.createFrom().item(new AlternativeIngredientForEdit("Chickpeas", "Pulses", 3, true, 2)));

		given()
				.when()
				.get("/api/v1/rules/alternative-ingredients/{id}", REFERENCE_ID)
				.then()
				.statusCode(200)
				.body("name", equalTo("Chickpeas"))
				.body("explanationForLlm", equalTo("Pulses"))
				.body("version", equalTo(3))
				.body("published", equalTo(true))
				.body("referenceCount", equalTo(2));

		verify(service).alternativeIngredientForEdit(authenticatedUser(), eq(REFERENCE_ID));
	}

	@ParameterizedTest
	@MethodSource("edits")
	void editStagesTheNameAndExplanation(String kind, Edit edit) {
		when(edit.apply(service, any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"name\": \"Chickpeas\", \"explanationForLlm\": \"Pulses\", \"baseVersion\": 3}")
				.when()
				.put("/api/v1/rules/{kind}/{id}", kind, REFERENCE_ID)
				.then()
				.statusCode(204);

		edit.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID), eq("Chickpeas"), eq("Pulses"), eq(3L));
	}

	@ParameterizedTest
	@MethodSource("reverts")
	void revertRevertsTheReference(String kind, Revert revert) {
		when(revert.apply(service, any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 3)
				.when()
				.delete("/api/v1/rules/{kind}/{id}", kind, REFERENCE_ID)
				.then()
				.statusCode(204);

		revert.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID), eq(3L));
	}

	@ParameterizedTest
	@MethodSource("translationsForEdit")
	void translationsForEditReturnsTheTranslationsByLanguage(String kind, TranslationsForEdit translationsForEdit) {
		when(translationsForEdit.apply(service, any(), any())).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.EL, new ReferenceDetails("Ρεβίθια", "Όσπρια", 2, false))));

		given()
				.when()
				.get("/api/v1/rules/{kind}/{id}/translations", kind, REFERENCE_ID)
				.then()
				.statusCode(200)
				.body("EL.name", equalTo("Ρεβίθια"))
				.body("EL.explanationForLlm", equalTo("Όσπρια"))
				.body("EL.version", equalTo(2))
				.body("EL.published", equalTo(false));

		translationsForEdit.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID));
	}

	@ParameterizedTest
	@MethodSource("translationStages")
	void stageTranslationStagesTheTranslationOfTheLanguage(String kind, TranslationStage stage) {
		when(stage.apply(service, any(), any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"name\": \"Ρεβίθια\", \"explanationForLlm\": \"Όσπρια\", \"baseVersion\": 2}")
				.when()
				.put("/api/v1/rules/{kind}/{id}/translations/{lang}", kind, REFERENCE_ID, "EL")
				.then()
				.statusCode(204);

		stage.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID), eq(RecipeLanguage.EL), eq("Ρεβίθια"), eq("Όσπρια"), eq(2L));
	}

	@ParameterizedTest
	@MethodSource("translationReverts")
	void revertTranslationRevertsTheTranslationOfTheLanguage(String kind, TranslationRevert revert) {
		when(revert.apply(service, any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 2)
				.when()
				.delete("/api/v1/rules/{kind}/{id}/translations/{lang}", kind, REFERENCE_ID, "EL")
				.then()
				.statusCode(204);

		revert.apply(verify(service), authenticatedUser(), eq(REFERENCE_ID), eq(RecipeLanguage.EL), eq(2L));
	}
}
