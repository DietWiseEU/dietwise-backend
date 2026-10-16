package eu.dietwise.services.v1;

import java.util.UUID;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the components for scoring of Trigger Ingredients: the grid of which Recommendations'
 * components for scoring a recipe ingredient matched to each Trigger Ingredient contributes to the score of the recipe.
 * The grid's cell toggles are staged; published master and recipe assessment are left untouched until a separate publish
 * step. Reserved for users with the ADMIN role.
 */
public interface BackofficeTriggerIngredientsService {
	/**
	 * The whole grid of components for scoring: the Recommendation columns and, per Trigger Ingredient, its effective
	 * name, translation completeness, links to those columns (published master overlaid by any Staged Change) and the
	 * Recommendations of its active Rules.
	 *
	 * @param user The editor; must have the ADMIN role
	 */
	Uni<TriggerIngredientRecommendationGrid> recommendationGrid(User user);

	/**
	 * Stage a single Trigger-Ingredient-to-Recommendation link to the given effective presence in the Working Copy,
	 * leaving published master untouched. Staging the presence master already has collapses the Staged Change. An
	 * unversioned toggle: a binary link to an absolute target presence carries no lost-update hazard, so no base version
	 * is taken and no stale-version check is performed. Removing the link to the Recommendation of an active Rule of the
	 * Trigger Ingredient is refused, since recipe assessment always counts it as the primary component.
	 *
	 * @param user                The editor; must have the ADMIN role
	 * @param triggerIngredientId The Trigger Ingredient whose link is being toggled
	 * @param recommendationId    The Recommendation the link points to
	 * @param present             The target effective presence of the link
	 * @throws eu.dietwise.common.dao.EntityInUseException If removing the link to the Recommendation of an active Rule
	 */
	Uni<Void> toggleRecommendation(User user, UUID triggerIngredientId, UUID recommendationId, boolean present);
}
