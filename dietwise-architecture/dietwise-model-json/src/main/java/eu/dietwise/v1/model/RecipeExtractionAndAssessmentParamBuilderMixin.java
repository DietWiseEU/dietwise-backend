package eu.dietwise.v1.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonSetter;
import eu.dietwise.v1.types.Viewport;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JsonPOJOBuilder(withPrefix = "")
public abstract class RecipeExtractionAndAssessmentParamBuilderMixin {
	@JsonSetter("viewport")
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public abstract ImmutableRecipeExtractionAndAssessmentParam.Builder viewport(Optional<Viewport> viewport);
}
