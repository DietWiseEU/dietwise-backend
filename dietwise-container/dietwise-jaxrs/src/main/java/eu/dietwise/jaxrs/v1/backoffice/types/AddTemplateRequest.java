package eu.dietwise.jaxrs.v1.backoffice.types;

/**
 * Request to add a Suggestion Template to a Rule for an existing AlternativeIngredient.
 *
 * @param alternativeIngredientId The chosen existing AlternativeIngredient id
 */
public record AddTemplateRequest(String alternativeIngredientId) {
}
