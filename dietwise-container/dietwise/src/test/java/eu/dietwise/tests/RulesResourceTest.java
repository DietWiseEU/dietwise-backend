package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.RuleField;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

/**
 * The endpoints of the {@code RulesResource} that are served by the {@link BackofficeRulesService}.
 */
@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class RulesResourceTest extends AdminResourceTest {
	private static final String RULE_ID = "d4d4d4d4-0000-4000-8000-000000000004";
	private static final UUID RECOMMENDATION_ID = UUID.fromString("b2b2b2b2-0000-4000-8000-000000000002");
	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("c3c3c3c3-0000-4000-8000-000000000003");
	private static final UUID ROLE_OR_TECHNIQUE_ID = UUID.fromString("e5e5e5e5-0000-4000-8000-000000000005");

	@InjectMock
	BackofficeRulesService service;

	@Test
	void listRulesReturnsTheRulesOfTheService() {
		var rule = ImmutableRule.builder()
				.id(new GenericRuleId(RULE_ID))
				.recommendation(new RecommendationImpl("Decrease red meat"))
				.triggerIngredient(new TriggerIngredientImpl("Beef"))
				.roleOrTechnique(new RoleOrTechniqueImpl("Main"))
				.rationale("Too much red meat")
				.build();
		when(service.listRules(any())).thenReturn(Uni.createFrom().item(List.of(new StagedRule(rule, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID,
				RuleChangeState.CHANGED, Set.of(RuleField.RATIONALE), Map.of(RecipeLanguage.EL, TranslationState.PARTIAL), Map.of(), Map.of(), 5))));

		given()
				.when()
				.get("/api/v1/rules")
				.then()
				.statusCode(200)
				.body("[0].id", equalTo(RULE_ID))
				.body("[0].recommendation", equalTo("Decrease red meat"))
				.body("[0].triggerIngredient", equalTo("Beef"))
				.body("[0].triggerIngredientId", equalTo(TRIGGER_INGREDIENT_ID.toString()))
				.body("[0].roleOrTechnique", equalTo("Main"))
				.body("[0].roleOrTechniqueId", equalTo(ROLE_OR_TECHNIQUE_ID.toString()))
				.body("[0].rationale", equalTo("Too much red meat"))
				.body("[0].active", equalTo(true))
				.body("[0].changeState", equalTo("CHANGED"))
				.body("[0].changedFields", contains("RATIONALE"))
				.body("[0].rationaleTranslations.EL", equalTo("PARTIAL"))
				.body("[0].version", equalTo(5));

		verify(service).listRules(authenticatedUser());
	}

	@Test
	void newRuleOptionsReturnsTheOptionsOfTheService() {
		when(service.newRuleOptions(any())).thenReturn(Uni.createFrom().item(new NewRuleOptions(
				List.of(new ReferenceOption(RECOMMENDATION_ID, "Decrease red meat")),
				List.of(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef")),
				List.of(new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "Main")))));

		given()
				.when()
				.get("/api/v1/rules/new-rule-options")
				.then()
				.statusCode(200)
				.body("recommendations[0].id", equalTo(RECOMMENDATION_ID.toString()))
				.body("recommendations[0].name", equalTo("Decrease red meat"))
				.body("triggerIngredients[0].name", equalTo("Beef"))
				.body("rolesOrTechniques[0].name", equalTo("Main"));

		verify(service).newRuleOptions(authenticatedUser());
	}

	@Test
	void createRuleStagesTheRuleAndReturnsItsId() {
		when(service.createRule(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(new GenericRuleId(RULE_ID)));

		given()
				.contentType(ContentType.JSON)
				.body("{\"recommendationId\": \"%s\", \"triggerIngredientId\": \"%s\", \"roleOrTechniqueId\": \"%s\"}"
						.formatted(RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID))
				.when()
				.post("/api/v1/rules")
				.then()
				.statusCode(200)
				.body("id", equalTo(RULE_ID));

		verify(service).createRule(authenticatedUser(), eq(RECOMMENDATION_ID), eq(TRIGGER_INGREDIENT_ID), eq(ROLE_OR_TECHNIQUE_ID));
	}

	@Test
	void createRuleStagesARuleWithoutRoleOrTechnique() {
		when(service.createRule(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(new GenericRuleId(RULE_ID)));

		given()
				.contentType(ContentType.JSON)
				.body("{\"recommendationId\": \"%s\", \"triggerIngredientId\": \"%s\"}".formatted(RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID))
				.when()
				.post("/api/v1/rules")
				.then()
				.statusCode(200)
				.body("id", equalTo(RULE_ID));

		verify(service).createRule(authenticatedUser(), eq(RECOMMENDATION_ID), eq(TRIGGER_INGREDIENT_ID), isNull());
	}

	@Test
	void discardNewRuleDiscardsTheRule() {
		when(service.discardNewRule(any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 5)
				.when()
				.delete("/api/v1/rules/{id}", RULE_ID)
				.then()
				.statusCode(204);

		verify(service).discardNewRule(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(5L));
	}

	@Test
	void stageRationaleStagesTheRationaleAndReturnsTheNewVersion() {
		when(service.stageRationale(any(), any(), any(), anyLong())).thenReturn(Uni.createFrom().item(6L));

		given()
				.contentType(ContentType.JSON)
				.body("{\"rationale\": \"Too much red meat\", \"baseVersion\": 5}")
				.when()
				.put("/api/v1/rules/{id}/rationale", RULE_ID)
				.then()
				.statusCode(200)
				.body("version", equalTo(6));

		verify(service).stageRationale(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq("Too much red meat"), eq(5L));
	}

	@Test
	void revertRationaleRevertsTheRationale() {
		when(service.revertRationale(any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 5)
				.when()
				.delete("/api/v1/rules/{id}/rationale", RULE_ID)
				.then()
				.statusCode(204);

		verify(service).revertRationale(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(5L));
	}

	@Test
	void setActiveStagesTheActiveState() {
		when(service.setActive(any(), any(), anyBoolean(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"active\": false, \"baseVersion\": 5}")
				.when()
				.put("/api/v1/rules/{id}/active", RULE_ID)
				.then()
				.statusCode(204);

		verify(service).setActive(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(false), eq(5L));
	}

	@Test
	void rationaleTranslationsForEditReturnsTheTranslationsByLanguage() {
		when(service.rationaleTranslationsForEdit(any(), any())).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.LT, new VersionedText("Per daug raudonos mėsos", 2))));

		given()
				.when()
				.get("/api/v1/rules/{id}/rationale-translations", RULE_ID)
				.then()
				.statusCode(200)
				.body("LT.text", equalTo("Per daug raudonos mėsos"))
				.body("LT.version", equalTo(2));

		verify(service).rationaleTranslationsForEdit(authenticatedUser(), eq(new GenericRuleId(RULE_ID)));
	}

	@Test
	void stageRationaleTranslationStagesTheTranslationOfTheLanguage() {
		when(service.stageRationaleTranslation(any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"rationale\": \"Per daug raudonos mėsos\", \"baseVersion\": 2}")
				.when()
				.put("/api/v1/rules/{id}/rationale-translations/{lang}", RULE_ID, "LT")
				.then()
				.statusCode(204);

		verify(service).stageRationaleTranslation(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(RecipeLanguage.LT),
				eq("Per daug raudonos mėsos"), eq(2L));
	}

	@Test
	void revertRationaleTranslationRevertsTheTranslationOfTheLanguage() {
		when(service.revertRationaleTranslation(any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.queryParam("baseVersion", 2)
				.when()
				.delete("/api/v1/rules/{id}/rationale-translations/{lang}", RULE_ID, "LT")
				.then()
				.statusCode(204);

		verify(service).revertRationaleTranslation(authenticatedUser(), eq(new GenericRuleId(RULE_ID)), eq(RecipeLanguage.LT), eq(2L));
	}
}
