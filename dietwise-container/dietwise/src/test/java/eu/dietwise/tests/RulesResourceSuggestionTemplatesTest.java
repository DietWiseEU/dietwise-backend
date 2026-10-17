package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.services.v1.BackofficeSuggestionTemplatesService;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

/**
 * The endpoints of the {@code RulesResource} that are served by the {@link BackofficeSuggestionTemplatesService}.
 */
@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class RulesResourceSuggestionTemplatesTest extends AdminResourceTest {
	private static final String RULE_ID = "d4d4d4d4-0000-4000-8000-000000000004";
	private static final String TEMPLATE_ID = "f6f6f6f6-0000-4000-8000-000000000006";
	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");

	@InjectMock
	BackofficeSuggestionTemplatesService service;

	@Test
	void listSuggestionTemplatesReturnsTheTemplatesOfTheRule() {
		var template = ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(TEMPLATE_ID))
				.alternative(new AlternativeIngredientImpl("Lentils"))
				.equivalence("1 cup")
				.build();
		when(service.listSuggestionTemplates(any(), any())).thenReturn(Uni.createFrom().item(List.of(new StagedSuggestionTemplate(template,
				ALTERNATIVE_INGREDIENT_ID, Set.of(SuggestionTemplateField.EQUIVALENCE),
				Map.of(SuggestionTemplateField.EQUIVALENCE, Map.of(RecipeLanguage.NL, TranslationState.MISSING)),
				Map.of(RecipeLanguage.EL, TranslationState.PRESENT), true, false, true, 3))));

		given()
				.when()
				.get("/api/v1/rules/{id}/suggestion-templates", RULE_ID)
				.then()
				.statusCode(200)
				.body("[0].id", equalTo(TEMPLATE_ID))
				.body("[0].alternativeIngredientId", equalTo(ALTERNATIVE_INGREDIENT_ID.toString()))
				.body("[0].alternativeIngredientName", equalTo("Lentils"))
				.body("[0].equivalence", equalTo("1 cup"))
				.body("[0].changedFields", contains("EQUIVALENCE"))
				.body("[0].translations.EQUIVALENCE.NL", equalTo("MISSING"))
				.body("[0].alternativeIngredientTranslations.EL", equalTo("PRESENT"))
				.body("[0].active", equalTo(true))
				.body("[0].version", equalTo(3));

		verify(service).listSuggestionTemplates(authenticatedUser(), eq(new GenericRuleId(RULE_ID)));
	}

	@Test
	void addSuggestionTemplateAddsTheAlternativeIngredientToTheRule() {
		when(service.addSuggestionTemplate(any(), any(), any())).thenReturn(Uni.createFrom().item(new AddedTemplate(UUID.fromString(TEMPLATE_ID), true)));

		given()
				.contentType(ContentType.JSON)
				.body("{\"alternativeIngredientId\": \"%s\"}".formatted(ALTERNATIVE_INGREDIENT_ID))
				.when()
				.post("/api/v1/rules/{id}/suggestion-templates", RULE_ID)
				.then()
				.statusCode(200)
				.body("templateId", equalTo(TEMPLATE_ID))
				.body("created", equalTo(true));

		verify(service).addSuggestionTemplate(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(ALTERNATIVE_INGREDIENT_ID));
	}

	@Test
	void discardSuggestionTemplateDiscardsTheTemplate() {
		when(service.discardSuggestionTemplate(any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 3)
				.when()
				.delete("/api/v1/rules/suggestion-templates/{templateId}", TEMPLATE_ID)
				.then()
				.statusCode(204);

		verify(service).discardSuggestionTemplate(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)), eq(3L));
	}

	@Test
	void stageSuggestionTemplateFieldStagesTheFieldAndReturnsTheNewVersion() {
		when(service.stageSuggestionTemplateField(any(), any(), any(), any(), anyLong())).thenReturn(Uni.createFrom().item(4L));

		given()
				.contentType(ContentType.JSON)
				.body("{\"value\": \"Not for babies\", \"baseVersion\": 3}")
				.when()
				.put("/api/v1/rules/suggestion-templates/{templateId}/{field}", TEMPLATE_ID, "RESTRICTION")
				.then()
				.statusCode(200)
				.body("version", equalTo(4));

		verify(service).stageSuggestionTemplateField(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)),
				eq(SuggestionTemplateField.RESTRICTION), eq("Not for babies"), eq(3L));
	}

	@Test
	void revertSuggestionTemplateFieldRevertsTheField() {
		when(service.revertSuggestionTemplateField(any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 3)
				.when()
				.delete("/api/v1/rules/suggestion-templates/{templateId}/{field}", TEMPLATE_ID, "RESTRICTION")
				.then()
				.statusCode(204);

		verify(service).revertSuggestionTemplateField(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)),
				eq(SuggestionTemplateField.RESTRICTION), eq(3L));
	}

	@Test
	void setSuggestionTemplateActiveStagesTheActiveState() {
		when(service.setSuggestionTemplateActive(any(), any(), anyBoolean(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"active\": false, \"baseVersion\": 3}")
				.when()
				.put("/api/v1/rules/suggestion-templates/{templateId}/active", TEMPLATE_ID)
				.then()
				.statusCode(204);

		verify(service).setSuggestionTemplateActive(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)), eq(false), eq(3L));
	}

	@Test
	void templateFieldTranslationsForEditReturnsTheTranslationsByLanguage() {
		when(service.templateFieldTranslationsForEdit(any(), any(), any())).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.NL, new VersionedText("1 kopje", 2))));

		given()
				.when()
				.get("/api/v1/rules/suggestion-templates/{templateId}/{field}/translations", TEMPLATE_ID, "EQUIVALENCE")
				.then()
				.statusCode(200)
				.body("NL.text", equalTo("1 kopje"))
				.body("NL.version", equalTo(2));

		verify(service).templateFieldTranslationsForEdit(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)),
				eq(SuggestionTemplateField.EQUIVALENCE));
	}

	@Test
	void stageTemplateFieldTranslationStagesTheTranslationOfTheLanguage() {
		when(service.stageTemplateFieldTranslation(any(), any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"value\": \"1 kopje\", \"baseVersion\": 2}")
				.when()
				.put("/api/v1/rules/suggestion-templates/{templateId}/{field}/translations/{lang}", TEMPLATE_ID, "EQUIVALENCE", "NL")
				.then()
				.statusCode(204);

		verify(service).stageTemplateFieldTranslation(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)),
				eq(SuggestionTemplateField.EQUIVALENCE), eq(RecipeLanguage.NL), eq("1 kopje"), eq(2L));
	}

	@Test
	void revertTemplateFieldTranslationRevertsTheTranslationOfTheLanguage() {
		when(service.revertTemplateFieldTranslation(any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 2)
				.when()
				.delete("/api/v1/rules/suggestion-templates/{templateId}/{field}/translations/{lang}", TEMPLATE_ID, "EQUIVALENCE", "NL")
				.then()
				.statusCode(204);

		verify(service).revertTemplateFieldTranslation(authenticatedUser(), eq(new GenericSuggestionTemplateId(TEMPLATE_ID)),
				eq(SuggestionTemplateField.EQUIVALENCE), eq(RecipeLanguage.NL), eq(2L));
	}

	@Test
	void alternativeIngredientOptionsReturnsTheOptionsOfTheService() {
		when(service.alternativeIngredientOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Lentils"))));

		given()
				.when()
				.get("/api/v1/rules/alternative-ingredients")
				.then()
				.statusCode(200)
				.body("[0].id", equalTo(ALTERNATIVE_INGREDIENT_ID.toString()))
				.body("[0].name", equalTo("Lentils"));

		verify(service).alternativeIngredientOptions(authenticatedUser());
	}
}
