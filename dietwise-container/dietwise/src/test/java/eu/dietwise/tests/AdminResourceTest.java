package eu.dietwise.tests;

import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;

/**
 * Base of the tests of the backoffice resources: the requests carry a token of the backoffice client, with the realm role
 * that grants backoffice access.
 */
@TestSecurity(user = ResourceTest.EMAIL, roles = "backoffice")
@OidcSecurity(claims = {
		@Claim(key = "sub", value = ResourceTest.IDM_ID),
		@Claim(key = "email", value = ResourceTest.EMAIL),
		@Claim(key = "azp", value = "backoffice")
})
abstract class AdminResourceTest extends ResourceTest {
}
