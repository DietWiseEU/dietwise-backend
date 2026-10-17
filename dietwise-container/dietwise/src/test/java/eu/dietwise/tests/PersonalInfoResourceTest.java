package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.PersonalInfoService;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Country;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class PersonalInfoResourceTest extends CitizenResourceTest {
	private static final PersonalInfo PERSONAL_INFO = ImmutablePersonalInfo.builder()
			.gender(BiologicalGender.FEMALE)
			.yearOfBirth(1979)
			.country(Country.GREECE)
			.build();

	@InjectMock
	PersonalInfoService personalInfoService;

	@Test
	void getPersonalInfoReturnsThePersonalInfoOfTheAuthenticatedUser() {
		when(personalInfoService.findByUser(any())).thenReturn(Uni.createFrom().item(PERSONAL_INFO));

		given()
				.when()
				.get("/api/v1/personal-info")
				.then()
				.statusCode(200)
				.body("gender", equalTo("FEMALE"))
				.body("yearOfBirth", equalTo(1979))
				.body("country", equalTo("GR"));

		verify(personalInfoService).findByUser(authenticatedUser());
	}

	@Test
	void storePersonalInfoStoresThePersonalInfoOfTheAuthenticatedUser() {
		when(personalInfoService.storeForUser(any(), any())).thenReturn(Uni.createFrom().item(PERSONAL_INFO));

		given()
				.contentType(ContentType.JSON)
				.body("""
						{"gender": "FEMALE", "yearOfBirth": 1979, "country": "GR"}
						""")
				.when()
				.post("/api/v1/personal-info")
				.then()
				.statusCode(200)
				.body("gender", equalTo("FEMALE"))
				.body("yearOfBirth", equalTo(1979))
				.body("country", equalTo("GR"));

		verify(personalInfoService).storeForUser(authenticatedUser(), eq(PERSONAL_INFO));
	}
}
