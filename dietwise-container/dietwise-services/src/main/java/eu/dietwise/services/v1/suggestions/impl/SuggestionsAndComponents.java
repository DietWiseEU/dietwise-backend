package eu.dietwise.services.v1.suggestions.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;

/**
 * The outcome of processing one recipe ingredient: the Suggestions for it and the components for scoring it contributes
 * to the score of the recipe. The {@code primaryComponent} is the component of the Recommendation of the Rule selected
 * for the ingredient, also contained in {@code components}; {@code null} when no Rule was selected.
 */
public record SuggestionsAndComponents(
		Ingredient ingredient,
		List<Suggestion> suggestions,
		Set<RecommendationComponent> components,
		RecommendationComponent primaryComponent
) {
	public SuggestionsAndComponents {
		suggestions = List.copyOf(suggestions);
		components = Set.copyOf(components);
	}

	public static SuggestionsAndComponents empty(Ingredient ingredient) {
		return withoutSuggestions(ingredient, Collections.emptySet());
	}

	public static SuggestionsAndComponents withoutSuggestions(Ingredient ingredient, Set<RecommendationComponent> components) {
		return new SuggestionsAndComponents(ingredient, Collections.emptyList(), components, null);
	}
}
