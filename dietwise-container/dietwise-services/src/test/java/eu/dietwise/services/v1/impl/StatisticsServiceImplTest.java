package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.InvalidRequestException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.interventions.InterventionAssignmentDao;
import eu.dietwise.dao.interventions.SuggestionEventDao;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.ImmutableSuggestionEvent;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.UiLanguage;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {
	private static final UUID USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String APPLICATION_ID = "recipewatch";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 26, 12, 30);
	private static final SuggestionTemplateId SUGGESTION_ID = new GenericSuggestionTemplateId("22222222-2222-3333-4444-555555555555");
	private static final String RECIPE_URL = "https://example.test/recipes/simple-pasta";
	private static final String RECIPE_NAME = "Simple Pasta";
	private static final UUID ASSESSMENT_ID = UUID.fromString("33333333-2222-3333-4444-555555555555");
	private static final UUID OTHER_USER_UUID = UUID.fromString("44444444-2222-3333-4444-555555555555");
	private static final SuggestionTemplateId OTHER_SUGGESTION_ID = new GenericSuggestionTemplateId("55555555-2222-3333-4444-555555555555");

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Mock
	private UserRecipeStatsEntityDao userRecipeStatsEntityDao;
	@Mock
	private UserStatsEntityDao userStatsEntityDao;
	@Mock
	private UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	@Mock
	private InterventionAssignmentDao interventionAssignmentDao;
	@Mock
	private SuggestionEventDao suggestionEventDao;
	@Mock
	private DateTimeService dateTimeService;
	@Mock
	private Authorization authorization;

	private StatisticsServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);
		sut = new StatisticsServiceImpl(persistenceContextFactory, userRecipeStatsEntityDao, userStatsEntityDao, userSuggestionStatsEntityDao, interventionAssignmentDao, suggestionEventDao, dateTimeService, authorization);
	}

	@Test
	void markUserActivityReturnsNullWhenUserIsNull() {
		User result = sut.markUserActivity(null)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isNull();
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(dateTimeService, never()).getNow();
		verify(userRecipeStatsEntityDao, never()).increaseTimesAssessed(any(), any(), any(), any(), any(), any());
		verify(userStatsEntityDao, never()).setLastSeen(any(), any(), any(), any());
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@Test
	void markUserActivityReturnsUserWithoutPersistenceWhenApplicationIdMissing() {
		User user = makeUserWithoutApplicationId();

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(dateTimeService, never()).getNow();
		verify(userRecipeStatsEntityDao, never()).increaseTimesAssessed(any(), any(), any(), any(), any(), any());
		verify(userStatsEntityDao, never()).setLastSeen(any(), any(), any(), any());
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@Test
	void assessedRecipeWithDetailsReturnsUserWithoutPersistenceWhenApplicationIdMissing() {
		User user = makeUserWithoutApplicationId();

		User result = sut.assessedRecipe(user, RECIPE_URL, RECIPE_NAME)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(dateTimeService, never()).getNow();
		verify(userRecipeStatsEntityDao, never()).increaseTimesAssessed(any(), any(), any(), any(), any(), any());
	}

	@Test
	void assessedRecipeWithDetailsUpdatesPerRecipeStatistics() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(userRecipeStatsEntityDao.increaseTimesAssessed(any(), any(), any(), any(), any(), any())).thenReturn(Uni.createFrom().item(3));

		User result = sut.assessedRecipe(user, RECIPE_URL, RECIPE_NAME)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(dateTimeService, times(1)).getNow();
		verify(userRecipeStatsEntityDao).increaseTimesAssessed(any(), eq(APPLICATION_ID), eq(USER_UUID), eq(RECIPE_URL), eq(RECIPE_NAME), eq(NOW));
	}

	@Test
	void markUserActivityIncreasesDaysLaunchedWhenLastSeenMoreThanOneDayAgo() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(userStatsEntityDao.setLastSeen(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(NOW.minusDays(2)));
		lenient().when(userStatsEntityDao.increaseDaysLaunched(any(), any(), any())).thenReturn(Uni.createFrom().item(1));

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(dateTimeService, times(1)).getNow();
		verify(userStatsEntityDao).setLastSeen(any(), eq(APPLICATION_ID), eq(USER_UUID), eq(NOW));
		verify(userStatsEntityDao).increaseDaysLaunched(any(), eq(APPLICATION_ID), eq(USER_UUID));
	}

	@Test
	void markUserActivitySkipsDaysLaunchedIncreaseWhenLastSeenWithinOneDay() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(userStatsEntityDao.setLastSeen(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(NOW.minusHours(12)));

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(dateTimeService, times(1)).getNow();
		verify(userStatsEntityDao).setLastSeen(any(), eq(APPLICATION_ID), eq(USER_UUID), eq(NOW));
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@FunctionalInterface
	interface CounterUpdate {
		Uni<Integer> apply(UserSuggestionStatsEntityDao dao, ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);
	}

	static Stream<Arguments> transitions() {
		return Stream.of(
				Arguments.of(SuggestionStatsTransition.INCREASE_ACCEPTED, (CounterUpdate) UserSuggestionStatsEntityDao::increaseTimesAccepted),
				Arguments.of(SuggestionStatsTransition.DECREASE_ACCEPTED, (CounterUpdate) UserSuggestionStatsEntityDao::decreaseTimesAccepted),
				Arguments.of(SuggestionStatsTransition.INCREASE_REJECTED, (CounterUpdate) UserSuggestionStatsEntityDao::increaseTimesRejected),
				Arguments.of(SuggestionStatsTransition.DECREASE_REJECTED, (CounterUpdate) UserSuggestionStatsEntityDao::decreaseTimesRejected)
		);
	}

	@ParameterizedTest
	@MethodSource("transitions")
	void updateSuggestionStatsUpdatesTheCounterOfTheTransition(SuggestionStatsTransition transition, CounterUpdate update) {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(update.apply(userSuggestionStatsEntityDao, any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));

		Integer result = sut.updateSuggestionStats(user, SUGGESTION_ID, null, transition).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		update.apply(verify(userSuggestionStatsEntityDao, times(1)), any(), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verifyNoMoreInteractions(userSuggestionStatsEntityDao);
		verify(authorization).requireLogin(user);
		verify(authorization).requireApplicationId(user);
		verifyNoInteractions(interventionAssignmentDao, suggestionEventDao);
	}

	@ParameterizedTest
	@MethodSource("transitions")
	void updateSuggestionStatsWithAssessmentAppendsASuggestionEventInTheTransactionOfTheCounter(SuggestionStatsTransition transition, CounterUpdate update) {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(interventionAssignmentDao.findById(any(), eq(ASSESSMENT_ID))).thenReturn(Uni.createFrom().item(Optional.of(makeAssignment(USER_UUID, APPLICATION_ID))));
		when(update.apply(userSuggestionStatsEntityDao, any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));
		when(suggestionEventDao.insert(any(), any())).thenReturn(Uni.createFrom().voidItem());

		Integer result = sut.updateSuggestionStats(user, SUGGESTION_ID, ASSESSMENT_ID, transition).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		ReactivePersistenceTxContext tx = persistenceContextFactory.getOpenedTransactions().getFirst();
		update.apply(verify(userSuggestionStatsEntityDao), eq(tx), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verify(suggestionEventDao).insert(tx, ImmutableSuggestionEvent.builder()
				.assessmentId(ASSESSMENT_ID)
				.suggestionTemplateId(SUGGESTION_ID)
				.action(transition)
				.createdAt(NOW)
				.build());
	}

	static Stream<Arguments> assignmentsTheUserCannotReport() {
		return Stream.of(
				Arguments.of("unknown assessment", Optional.empty()),
				Arguments.of("assessment of another user", Optional.of(makeAssignment(OTHER_USER_UUID, APPLICATION_ID))),
				Arguments.of("assessment of another application", Optional.of(makeAssignment(USER_UUID, "rca")))
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("assignmentsTheUserCannotReport")
	void updateSuggestionStatsWithAnAssessmentTheUserCannotReportIsNotFoundAndWritesNothing(String description, Optional<InterventionAssignment> assignment) {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(interventionAssignmentDao.findById(any(), eq(ASSESSMENT_ID))).thenReturn(Uni.createFrom().item(assignment));

		assertThatThrownBy(() -> sut.updateSuggestionStats(user, SUGGESTION_ID, ASSESSMENT_ID, SuggestionStatsTransition.INCREASE_ACCEPTED).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(EntityNotFoundException.class);

		verifyNoInteractions(userSuggestionStatsEntityDao, suggestionEventDao);
	}

	@Test
	void updateSuggestionStatsWithASuggestionNotShownInTheAssessmentIsInvalidAndWritesNothing() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(interventionAssignmentDao.findById(any(), eq(ASSESSMENT_ID))).thenReturn(Uni.createFrom().item(Optional.of(makeAssignment(USER_UUID, APPLICATION_ID))));

		assertThatThrownBy(() -> sut.updateSuggestionStats(user, OTHER_SUGGESTION_ID, ASSESSMENT_ID, SuggestionStatsTransition.INCREASE_ACCEPTED).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(InvalidRequestException.class);

		verifyNoInteractions(userSuggestionStatsEntityDao, suggestionEventDao);
	}

	private static InterventionAssignment makeAssignment(UUID userId, String applicationId) {
		return ImmutableInterventionAssignment.builder()
				.assessmentId(ASSESSMENT_ID)
				.userId(userId)
				.applicationId(applicationId)
				.interventionId(1)
				.uiLang(UiLanguage.EN)
				.recipeUrl(RECIPE_URL)
				.createdAt(NOW.minusMinutes(5))
				.suggestionTemplateIds(Set.of(SUGGESTION_ID))
				.build();
	}

	private static User makeUserWithApplicationId(String applicationId) {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.noneOf(Role.class))
				.applicationId(applicationId)
				.build();
	}

	private static User makeUserWithoutApplicationId() {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.noneOf(Role.class))
				.build();
	}
}
