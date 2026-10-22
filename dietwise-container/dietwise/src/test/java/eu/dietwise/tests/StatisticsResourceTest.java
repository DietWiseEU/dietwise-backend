package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The four endpoints have the same shape: each updates one counter of a Suggestion Template by applying its own
 * {@link SuggestionStatsTransition} through the {@link StatisticsService}.
 */
@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class StatisticsResourceTest extends CitizenResourceTest {
	private static final String SUGGESTION_ID = "3f2a1b0c-9d8e-4f7a-b6c5-d4e3f2a1b0c9";

	static Stream<Arguments> endpoints() {
		return Stream.of(
				Arguments.of("increaseTimesAccepted", SuggestionStatsTransition.INCREASE_ACCEPTED),
				Arguments.of("decreaseTimesAccepted", SuggestionStatsTransition.DECREASE_ACCEPTED),
				Arguments.of("increaseTimesRejected", SuggestionStatsTransition.INCREASE_REJECTED),
				Arguments.of("decreaseTimesRejected", SuggestionStatsTransition.DECREASE_REJECTED)
		);
	}

	@ParameterizedTest
	@MethodSource("endpoints")
	void updatesTheCounterOfTheSuggestionForTheAuthenticatedUser(String endpoint, SuggestionStatsTransition transition) {
		when(statisticsService.updateSuggestionStats(any(), any(), any())).thenReturn(Uni.createFrom().item(7));

		given()
				.contentType(ContentType.JSON)
				.body("""
						{"suggestionId": "%s"}
						""".formatted(SUGGESTION_ID))
				.when()
				.post("/api/v1/statistics/" + endpoint)
				.then()
				.statusCode(200)
				.body("updatedValue", equalTo(7));

		verify(statisticsService).updateSuggestionStats(authenticatedUser(), eq(new GenericSuggestionTemplateId(SUGGESTION_ID)), eq(transition));
	}

	@ParameterizedTest
	@MethodSource("endpoints")
	void rejectsARequestWithoutSuggestion(String endpoint, SuggestionStatsTransition transition) {
		given()
				.contentType(ContentType.JSON)
				.body("{}")
				.when()
				.post("/api/v1/statistics/" + endpoint)
				.then()
				.statusCode(400);

		verify(statisticsService, never()).updateSuggestionStats(any(), any(), any());
	}
}
