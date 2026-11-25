package eu.dietwise.v1.model;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableIngredient.Builder.class)
public abstract class IngredientMixin {
}
