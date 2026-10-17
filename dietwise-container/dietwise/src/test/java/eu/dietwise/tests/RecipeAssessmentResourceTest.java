package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class RecipeAssessmentResourceTest extends CitizenResourceTest {
	private static final String RECIPE_URL = "https://example.test/recipe";
	private static final RecipeAssessmentMessage MORE_THAN_ONE_RECIPES_MESSAGE = new MoreThanOneRecipesAssessmentMessage(2);
	private static final RecipeAssessmentMessage SCORING_MESSAGE = new ScoringRecipeAssessmentMessage(ImmutableScoringData.builder()
			.totalNumberOfRecomendations(1)
			.recommendationWeights(Map.of())
			.humanFriendlyDisplays(Map.of(new RecommendationComponentNameImpl("fruit"), "Eat more fruit"))
			.recommendationsPerIngredient(Map.of(new GenericIngredientId("apple"), Set.of(new RecommendationComponentNameImpl("fruit"))))
			.primaryComponentPerIngredient(Map.of(new GenericIngredientId("apple"), new RecommendationComponentNameImpl("fruit")))
			.build());
	private static final RecipeAssessmentMessage ERROR_MESSAGE = new RecipeAssessmentErrorMessage(List.of("No recipe found"));

	@InjectMock
	RecipeAssessmentService service;

	@Test
	void assessMarkdownRecipeStreamsTheMessagesOfTheServiceForTheAuthenticatedUser() {
		when(service.assessMarkdownRecipe(any(), any())).thenReturn(Multi.createFrom().items(MORE_THAN_ONE_RECIPES_MESSAGE, SCORING_MESSAGE));

		var body = given()
				.contentType(ContentType.JSON)
				.body("""
						{"url": "%s", "pageContent": "Recipe text", "lang": "en"}
						""".formatted(RECIPE_URL))
				.when()
				.post("/api/v1/recipe/assess/markdown")
				.then()
				.statusCode(200)
				.extract().asString();

		verify(service).assessMarkdownRecipe(
				authenticatedUser(),
				eq(ImmutableRecipeAssessmentParam.builder().url(RECIPE_URL).pageContent("Recipe text").lang(RecipeLanguage.EN).build()));
		assertThat(body.lines()).containsExactly(
				"{\"type\":\"MORE_THAN_ONE_RECIPE\",\"numberOfRecipes\":2}",
				"{\"type\":\"SCORING\",\"scoringData\":{\"totalNumberOfRecomendations\":1,\"recommendationWeights\":{},"
						+ "\"humanFriendlyDisplays\":{\"fruit\":\"Eat more fruit\"},\"recommendationsPerIngredient\":{\"apple\":[\"fruit\"]},"
						+ "\"primaryComponentPerIngredient\":{\"apple\":\"fruit\"}}}"
		);
	}

	@Test
	void extractAndAssessRecipeFromUrlStreamsTheMessagesOfTheServiceForTheAuthenticatedUser() {
		when(service.extractAndAssessRecipeFromUrl(any(), any())).thenReturn(Multi.createFrom().items(ERROR_MESSAGE));

		var body = given()
				.contentType(ContentType.JSON)
				.body("""
						{"url": "%s", "lang": "el"}
						""".formatted(RECIPE_URL))
				.when()
				.post("/api/v1/recipe/assess/url")
				.then()
				.statusCode(200)
				.extract().asString();

		verify(service).extractAndAssessRecipeFromUrl(
				authenticatedUser(),
				eq(ImmutableRecipeExtractionAndAssessmentParam.builder().url(RECIPE_URL).lang(RecipeLanguage.EL).build()));
		assertThat(body.lines()).containsExactly("{\"type\":\"ERROR\",\"errors\":[\"No recipe found\"]}");
	}
}
