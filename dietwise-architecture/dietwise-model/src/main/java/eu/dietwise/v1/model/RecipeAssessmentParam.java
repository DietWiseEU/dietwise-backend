package eu.dietwise.v1.model;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.UiLanguage;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentParam {
	String getUrl();

	String getPageContent();

	@Nullable
	String getJsonLdContent();

	RecipeLanguage getLang();

	@Nullable
	Country getCountryOverride();

	/**
	 * The language of the client's user interface; absent for clients that show no Intervention.
	 */
	@Nullable
	UiLanguage getUiLang();
}
