package eu.dietwise.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import eu.dietwise.common.v1.model.ImmutableUserData;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.model.UserData;
import eu.dietwise.common.v1.types.UserId;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.services.nondomain.UserService;
import eu.dietwise.services.v1.StatisticsService;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base of the tests that drive the JAX-RS resources over HTTP, with the services mocked. The subclasses choose the token
 * the requests carry, by annotating the test with {@code @TestSecurity} and {@code @OidcSecurity} with the claims below;
 * the stubbing of {@link #stubAuthentication()} lets the {@code DietwiseAuthenticationFilter} turn these claims into the
 * {@link User} with {@link #USER_ID}. Each test class is a {@code @QuarkusTest} with the {@link OfflineTestProfile}, as
 * Quarkus does not look up the test profile in the superclasses.
 */
abstract class ResourceTest {
	static final String IDM_ID = "8d1c5e2a-3f4b-4c6d-9e7f-0a1b2c3d4e5f";
	static final String EMAIL = "user@example.test";
	static final UserId USER_ID = new UserIdImpl("5b0e9a8c-1d2e-4f3a-8b7c-6d5e4f3a2b1c");
	static final Uni<Void> VOID = Uni.createFrom().voidItem();

	@InjectMock
	UserService userService;

	@InjectMock
	StatisticsService statisticsService;

	@BeforeEach
	void stubAuthentication() {
		Uni<UserData> userData = Uni.createFrom().item(ImmutableUserData.builder().id(USER_ID).deletedAt(Optional.empty()).build());
		when(userService.findOrCreateByIdmId(IDM_ID)).thenReturn(userData);
		when(userService.findByIdmId(IDM_ID)).thenReturn(userData);
		when(statisticsService.markUserActivity(any())).thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0, User.class)));
	}

	/**
	 * Match the {@link User} that the {@code DietwiseAuthenticationFilter} made from the claims of the token.
	 */
	static User authenticatedUser() {
		return argThat(user -> user != null && USER_ID.equals(user.getId()));
	}
}
