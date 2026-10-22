package eu.dietwise.services.v1;

import java.util.UUID;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.v1.types.SuggestionStatsTransition;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

public interface StatisticsService {
	/**
	 * Called every time the application sees an activity by the user to update usage statistics.
	 *
	 * @param user The user
	 * @return The same user, for convenience
	 */
	Uni<User> markUserActivity(User user);

	Uni<User> assessedRecipe(User user);

	Uni<User> assessedRecipe(User user, String recipeUrl, String recipeName);

	/**
	 * Apply the transition to the user's SuggestionStats of the given suggestion, return the new value of the changed
	 * counter. Increases do not increment if times accepted plus times rejected is greater than times suggested;
	 * decreases do not decrement below zero.
	 * <p>
	 * With an assessment id, the transition is also appended as a Suggestion Event of the Intervention Assignment of
	 * that assessment, in the same transaction, even if the counter does not change.
	 *
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the assessment has no Intervention Assignment of
	 *                                                        this user and application
	 * @throws eu.dietwise.common.types.InvalidRequestException If the suggestion was not shown in that assessment
	 */
	Uni<Integer> updateSuggestionStats(User user, SuggestionTemplateId suggestionId, @Nullable UUID assessmentId, SuggestionStatsTransition transition);
}
