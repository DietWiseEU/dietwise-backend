package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.InvalidRequestException;
import eu.dietwise.common.types.Nullable;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.interventions.InterventionAssignmentDao;
import eu.dietwise.dao.interventions.SuggestionEventDao;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.interventions.ImmutableSuggestionEvent;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class StatisticsServiceImpl implements StatisticsService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserRecipeStatsEntityDao userRecipeStatsEntityDao;
	private final UserStatsEntityDao userStatsEntityDao;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	private final InterventionAssignmentDao interventionAssignmentDao;
	private final SuggestionEventDao suggestionEventDao;
	private final DateTimeService dateTimeService;
	private final Authorization authorization;

	public StatisticsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserRecipeStatsEntityDao userRecipeStatsEntityDao,
			UserStatsEntityDao userStatsEntityDao,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao,
			InterventionAssignmentDao interventionAssignmentDao,
			SuggestionEventDao suggestionEventDao,
			DateTimeService dateTimeService,
			Authorization authorization
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userRecipeStatsEntityDao = userRecipeStatsEntityDao;
		this.userStatsEntityDao = userStatsEntityDao;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
		this.interventionAssignmentDao = interventionAssignmentDao;
		this.suggestionEventDao = suggestionEventDao;
		this.dateTimeService = dateTimeService;
		this.authorization = authorization;
	}

	@Override
	public Uni<User> markUserActivity(User user) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		LocalDateTime now = dateTimeService.getNow();
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		return persistenceContextFactory.withTransaction(tx -> forc(
				userStatsEntityDao.setLastSeen(tx, applicationId, userId, now),
				startDayOfUseIfNeeded(tx, user, applicationId, userId, now)
		));
	}

	/**
	 * A day of use starts with the first activity of a calendar day: it counts as one more day launched and its time
	 * becomes the last launched.
	 */
	private Function<? super LocalDateTime, Uni<? extends User>> startDayOfUseIfNeeded(ReactivePersistenceTxContext tx, User user, String applicationId, UUID userId, LocalDateTime now) {
		return lastSeen -> {
			if (lastSeen == null || lastSeen.toLocalDate().isBefore(now.toLocalDate())) {
				return userStatsEntityDao.increaseDaysLaunched(tx, applicationId, userId)
						.chain(() -> userStatsEntityDao.setLastLaunched(tx, applicationId, userId, now))
						.replaceWith(user);
			} else {
				return Uni.createFrom().item(user);
			}
		};
	}

	@Override
	public Uni<User> assessedRecipe(User user) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		return persistenceContextFactory.withTransaction(tx ->
				userStatsEntityDao.increaseRecipesAssessed(tx, applicationId, userId).replaceWith(user)
		);
	}

	@Override
	public Uni<User> assessedRecipe(User user, String recipeUrl, String recipeName) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		LocalDateTime now = dateTimeService.getNow();
		return persistenceContextFactory.withTransaction(tx ->
				userRecipeStatsEntityDao.increaseTimesAssessed(tx, applicationId, userId, recipeUrl, recipeName, now).replaceWith(user)
		);
	}

	@Override
	public Uni<Integer> updateSuggestionStats(User user, SuggestionTemplateId suggestionId, @Nullable UUID assessmentId, SuggestionStatsTransition transition) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		if (assessmentId == null) {
			return persistenceContextFactory.withTransaction(tx -> updateCounter(tx, applicationId, user, suggestionId, transition));
		} else {
			return persistenceContextFactory.withTransaction(tx -> interventionAssignmentDao.findById(tx, assessmentId)
					.invoke(assignment -> requireSuggestionShownToUser(assignment, assessmentId, user, applicationId, suggestionId))
					.chain(() -> updateCounter(tx, applicationId, user, suggestionId, transition))
					.call(() -> suggestionEventDao.insert(tx, ImmutableSuggestionEvent.builder()
							.assessmentId(assessmentId)
							.suggestionTemplateId(suggestionId)
							.action(transition)
							.createdAt(dateTimeService.getNow())
							.build()))
			);
		}
	}

	private Uni<Integer> updateCounter(ReactivePersistenceTxContext tx, String applicationId, User user, SuggestionTemplateId suggestionId, SuggestionStatsTransition transition) {
		return switch (transition) {
			case INCREASE_ACCEPTED -> userSuggestionStatsEntityDao.increaseTimesAccepted(tx, applicationId, user, suggestionId);
			case DECREASE_ACCEPTED -> userSuggestionStatsEntityDao.decreaseTimesAccepted(tx, applicationId, user, suggestionId);
			case INCREASE_REJECTED -> userSuggestionStatsEntityDao.increaseTimesRejected(tx, applicationId, user, suggestionId);
			case DECREASE_REJECTED -> userSuggestionStatsEntityDao.decreaseTimesRejected(tx, applicationId, user, suggestionId);
		};
	}

	private void requireSuggestionShownToUser(Optional<InterventionAssignment> assignment, UUID assessmentId, User user, String applicationId, SuggestionTemplateId suggestionId) {
		UUID userId = UUID.fromString(user.getId().asString());
		InterventionAssignment assignmentOfUser = assignment
				.filter(a -> a.getUserId().equals(userId) && a.getApplicationId().equals(applicationId))
				.orElseThrow(() -> new EntityNotFoundException(InterventionAssignment.class, assessmentId));
		if (!assignmentOfUser.getSuggestionTemplateIds().contains(suggestionId)) {
			throw new InvalidRequestException("Suggestion was not shown in the assessment: suggestion " + suggestionId.asString() + ", assessment " + assessmentId);
		}
	}
}
