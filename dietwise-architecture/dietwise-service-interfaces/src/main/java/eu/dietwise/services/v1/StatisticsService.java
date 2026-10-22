package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.SuggestionStatsTransition;
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
	 */
	Uni<Integer> updateSuggestionStats(User user, SuggestionTemplateId suggestionId, SuggestionStatsTransition transition);
}
