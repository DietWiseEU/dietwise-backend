package eu.dietwise.services.model.interventions;

import java.util.Map;

import eu.dietwise.v1.types.RecipeLanguage;
import org.immutables.value.Value;

/**
 * An Intervention with its text in each language it has one; the "no Intervention" arm has no texts.
 */
@Value.Immutable
public interface TranslatedIntervention {
	int getId();

	Map<RecipeLanguage, String> getTexts();
}
