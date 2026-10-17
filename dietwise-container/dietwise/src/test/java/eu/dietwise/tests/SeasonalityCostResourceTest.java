package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.services.v1.BackofficeSeasonalityCostService;
import eu.dietwise.services.v1.types.CostCell;
import eu.dietwise.services.v1.types.SeasonalityCell;
import eu.dietwise.services.v1.types.SeasonalityCostGrid;
import eu.dietwise.services.v1.types.SeasonalityCostRow;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class SeasonalityCostResourceTest extends AdminResourceTest {
	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");

	@InjectMock
	BackofficeSeasonalityCostService service;

	@Test
	void gridReturnsTheGridOfTheService() {
		when(service.grid(any())).thenReturn(Uni.createFrom().item(new SeasonalityCostGrid(
				List.of(Country.GREECE, Country.BELGIUM),
				List.of(new SeasonalityCostRow(ALTERNATIVE_INGREDIENT_ID, "Tomatoes", true,
						Map.of(Country.GREECE, new SeasonalityCell(5, 9, true, 2)),
						Map.of(Country.GREECE, new CostCell(Cost.LO, false, 1), Country.BELGIUM, new CostCell(null, true, 4)))))));

		given()
				.when()
				.get("/api/v1/seasonality-cost")
				.then()
				.statusCode(200)
				.body("countries", contains("GR", "BE"))
				.body("rows[0].id", equalTo(ALTERNATIVE_INGREDIENT_ID.toString()))
				.body("rows[0].name", equalTo("Tomatoes"))
				.body("rows[0].seasonality.GR.monthFrom", equalTo(5))
				.body("rows[0].seasonality.GR.monthTo", equalTo(9))
				.body("rows[0].seasonality.GR.staged", equalTo(true))
				.body("rows[0].cost.GR.cost", equalTo("LO"))
				.body("rows[0].cost.GR.version", equalTo(1))
				.body("rows[0].cost.BE.cost", nullValue())
				.body("rows[0].cost.BE.staged", equalTo(true));

		verify(service).grid(authenticatedUser());
	}

	@Test
	void stageSeasonalityStagesTheMonthRangeOfTheCountry() {
		when(service.stageSeasonality(any(), any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"monthFrom\": 5, \"monthTo\": 9, \"baseVersion\": 2}")
				.when()
				.put("/api/v1/seasonality-cost/{id}/seasonality/{country}", ALTERNATIVE_INGREDIENT_ID, "GR")
				.then()
				.statusCode(204);

		verify(service).stageSeasonality(authenticatedUser(), eq(ALTERNATIVE_INGREDIENT_ID), eq(Country.GREECE), eq(5), eq(9), eq(2L));
	}

	@Test
	void stageCostStagesTheCostOfTheCountry() {
		when(service.stageCost(any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"cost\": \"HI\", \"baseVersion\": 2}")
				.when()
				.put("/api/v1/seasonality-cost/{id}/cost/{country}", ALTERNATIVE_INGREDIENT_ID, "GR")
				.then()
				.statusCode(204);

		verify(service).stageCost(authenticatedUser(), eq(ALTERNATIVE_INGREDIENT_ID), eq(Country.GREECE), eq(Cost.HI), eq(2L));
	}

	@ParameterizedTest
	@ValueSource(strings = {"null", "\"\"", "\" \""})
	void stageCostClearsTheCostOfTheCountryForANullOrBlankCost(String cost) {
		when(service.stageCost(any(), any(), any(), any(), anyLong())).thenReturn(VOID);

		given()
				.contentType(ContentType.JSON)
				.body("{\"cost\": %s, \"baseVersion\": 2}".formatted(cost))
				.when()
				.put("/api/v1/seasonality-cost/{id}/cost/{country}", ALTERNATIVE_INGREDIENT_ID, "GR")
				.then()
				.statusCode(204);

		verify(service).stageCost(authenticatedUser(), eq(ALTERNATIVE_INGREDIENT_ID), eq(Country.GREECE), isNull(), eq(2L));
	}
}
