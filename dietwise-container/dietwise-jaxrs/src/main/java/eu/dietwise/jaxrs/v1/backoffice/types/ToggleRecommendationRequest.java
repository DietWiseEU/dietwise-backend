package eu.dietwise.jaxrs.v1.backoffice.types;

/**
 * Request to stage a scoring-component link from an Alternative or Trigger Ingredient to a Recommendation to an absolute
 * target presence in the Working Copy.
 *
 * @param present The target effective presence of the link ({@code true} to link, {@code false} to unlink)
 */
public record ToggleRecommendationRequest(boolean present) {
}
