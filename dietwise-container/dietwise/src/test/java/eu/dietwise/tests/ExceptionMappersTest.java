package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.EntityInUseException;
import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.services.v1.PersonalInfoService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each exception mapper turns the failure of a service into its HTTP status. The failing service is the
 * {@link PersonalInfoService} behind {@code GET /api/v1/personal-info}, the simplest endpoint returning a {@code Uni}.
 */
@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class ExceptionMappersTest extends CitizenResourceTest {
	@InjectMock
	PersonalInfoService personalInfoService;

	static Stream<Arguments> exceptionsAndStatuses() {
		return Stream.of(
				Arguments.of(new EntityNotFoundException("not found"), 404),
				Arguments.of(new NotAuthenticatedException("not authenticated"), 401),
				Arguments.of(new NotAuthorizedException("not authorized"), 403),
				Arguments.of(new DuplicateBusinessKeyException("duplicate"), 409),
				Arguments.of(new EntityInUseException("in use"), 409),
				Arguments.of(new StaleVersionException(Object.class, "id"), 409)
		);
	}

	@ParameterizedTest
	@MethodSource("exceptionsAndStatuses")
	void mapsTheFailureOfTheServiceToItsStatus(RuntimeException exception, int status) {
		when(personalInfoService.findByUser(any())).thenReturn(Uni.createFrom().failure(exception));

		given()
				.when()
				.get("/api/v1/personal-info")
				.then()
				.statusCode(status);
	}
}
