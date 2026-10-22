package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class StatisticsServiceImpl implements StatisticsService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserRecipeStatsEntityDao userRecipeStatsEntityDao;
	private final UserStatsEntityDao userStatsEntityDao;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	private final DateTimeService dateTimeService;
	private final Authorization authorization;

	public StatisticsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserRecipeStatsEntityDao userRecipeStatsEntityDao,
			UserStatsEntityDao userStatsEntityDao,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao,
			DateTimeService dateTimeService,
			Authorization authorization
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userRecipeStatsEntityDao = userRecipeStatsEntityDao;
		this.userStatsEntityDao = userStatsEntityDao;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
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
				increaseDaysLaunchedIfNeeded(tx, user, user.getApplicationId().get(), userId, now)
		));
	}

	private Function<? super LocalDateTime, Uni<? extends User>> increaseDaysLaunchedIfNeeded(ReactivePersistenceTxContext tx, User user, String applicationId, UUID userId, LocalDateTime now) {
		return lastSeen -> {
			if (lastSeen == null || lastSeen.isBefore(now.minusDays(1))) {
				return userStatsEntityDao.increaseDaysLaunched(tx, applicationId, userId).replaceWith(user);
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
	public Uni<Integer> updateSuggestionStats(User user, SuggestionTemplateId suggestionId, SuggestionStatsTransition transition) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return persistenceContextFactory.withTransaction(tx -> switch (transition) {
			case INCREASE_ACCEPTED -> userSuggestionStatsEntityDao.increaseTimesAccepted(tx, applicationId, user, suggestionId);
			case DECREASE_ACCEPTED -> userSuggestionStatsEntityDao.decreaseTimesAccepted(tx, applicationId, user, suggestionId);
			case INCREASE_REJECTED -> userSuggestionStatsEntityDao.increaseTimesRejected(tx, applicationId, user, suggestionId);
			case DECREASE_REJECTED -> userSuggestionStatsEntityDao.decreaseTimesRejected(tx, applicationId, user, suggestionId);
		});
	}
}
