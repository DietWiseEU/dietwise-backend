package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Viewport;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeExtractionAndAssessmentParam {
	String getUrl();

	Optional<Viewport> getViewport();

	RecipeLanguage getLang();

	/**
	 * The language of the client's user interface; absent for clients that show no Intervention.
	 */
	@Nullable
	RecipeLanguage getUiLang();
}
