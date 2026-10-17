package eu.dietwise.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import eu.dietwise.common.v1.model.ImmutableUserData;
import eu.dietwise.services.v1.AccountService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.ClaimType;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OfflineTestProfile.class)
class UsersResourceTest extends CitizenResourceTest {
	/**
	 * An authentication time far in the future, since an annotation cannot carry the current time: the resource only
	 * rejects an authentication older than five minutes.
	 */
	private static final String FRESH_AUTH_TIME = "4102444800";
	private static final String STALE_AUTH_TIME = "946684800";

	@InjectMock
	AccountService accountService;

	@Test
	void getAccountDeletionConfigReturnsTheConfiguredRealmUrlWithoutTrailingSlashes() {
		given()
				.when()
				.get("/api/v1/users/account-deletion-config")
				.then()
				.statusCode(200)
				.body("realmUrl", equalTo("http://keycloak.test/realms/dietwise"));
	}

	@Test
	@TestSecurity(user = EMAIL)
	@OidcSecurity(claims = {
			@Claim(key = "sub", value = IDM_ID),
			@Claim(key = "email", value = EMAIL),
			@Claim(key = "azp", value = "recipewatch"),
			@Claim(key = "auth_time", value = FRESH_AUTH_TIME, type = ClaimType.LONG)
	})
	void deleteCurrentUserDeletesTheAccountOfAFreshAuthentication() {
		when(accountService.deleteAccount(any())).thenReturn(VOID);

		given().when().delete("/api/v1/users/me").then().statusCode(204);

		verify(accountService).deleteAccount(authenticatedUser());
	}

	@Test
	@TestSecurity(user = EMAIL)
	@OidcSecurity(claims = {
			@Claim(key = "sub", value = IDM_ID),
			@Claim(key = "email", value = EMAIL),
			@Claim(key = "azp", value = "recipewatch"),
			@Claim(key = "auth_time", value = STALE_AUTH_TIME, type = ClaimType.LONG)
	})
	void deleteCurrentUserRejectsAStaleAuthentication() {
		given().when().delete("/api/v1/users/me").then().statusCode(401);

		verifyNoInteractions(accountService);
	}

	@Test
	void deleteCurrentUserRejectsAMissingAuthenticationTime() {
		given().when().delete("/api/v1/users/me").then().statusCode(401);

		verifyNoInteractions(accountService);
	}

	@Test
	@TestSecurity(user = EMAIL)
	@OidcSecurity(claims = {
			@Claim(key = "sub", value = IDM_ID),
			@Claim(key = "email", value = EMAIL),
			@Claim(key = "azp", value = "recipewatch"),
			@Claim(key = "auth_time", value = "yesterday", type = ClaimType.STRING)
	})
	void deleteCurrentUserRejectsANonNumericAuthenticationTime() {
		given().when().delete("/api/v1/users/me").then().statusCode(401);

		verifyNoInteractions(accountService);
	}

	@Test
	@TestSecurity(user = EMAIL)
	@OidcSecurity(claims = {
			@Claim(key = "sub", value = IDM_ID),
			@Claim(key = "email", value = EMAIL),
			@Claim(key = "azp", value = "recipewatch"),
			@Claim(key = "auth_time", value = FRESH_AUTH_TIME, type = ClaimType.LONG)
	})
	void deleteCurrentUserSucceedsWithoutDeletingAnAlreadyDeletedAccount() {
		when(userService.findByIdmId(IDM_ID)).thenReturn(Uni.createFrom().item(
				ImmutableUserData.builder().id(USER_ID).deletedAt(Optional.of(LocalDateTime.of(2026, 1, 1, 0, 0))).build()));

		given().when().delete("/api/v1/users/me").then().statusCode(204);

		verifyNoInteractions(accountService);
	}
}
