package eu.dietwise.services.model.recommendations;

import java.util.Optional;

import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.TypeOfRecommendation;
import org.immutables.value.Value;

@Value.Immutable
public interface RecommendationComponent {
	/**
	 * The Recommendation by its master (English) name: the language-independent identity that the Recommendation weights
	 * and the Rules refer to. The component for scoring, explanation and display are localized; this is not.
	 */
	Recommendation getRecommendation();

	RecommendationComponentName getComponentForScoring();

	TypeOfRecommendation getTypeOfRecommendation();

	Optional<String> getExplanationForLlm();

	Optional<String> getHumanFriendlyDisplay();
}
