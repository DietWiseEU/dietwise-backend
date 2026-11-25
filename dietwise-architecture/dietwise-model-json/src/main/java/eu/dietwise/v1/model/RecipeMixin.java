package eu.dietwise.v1.model;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableRecipe.Builder.class)
public class RecipeMixin {
}
