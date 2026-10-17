package eu.dietwise.tests;

import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;

/**
 * Base of the tests of the resources that the apps call: the requests carry a token of the MyRecipeWatch client.
 */
@TestSecurity(user = ResourceTest.EMAIL)
@OidcSecurity(claims = {
		@Claim(key = "sub", value = ResourceTest.IDM_ID),
		@Claim(key = "email", value = ResourceTest.EMAIL),
		@Claim(key = "azp", value = "recipewatch")
})
abstract class CitizenResourceTest extends ResourceTest {
}
