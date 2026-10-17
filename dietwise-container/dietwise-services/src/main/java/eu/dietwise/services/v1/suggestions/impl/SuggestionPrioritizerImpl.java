package eu.dietwise.services.v1.suggestions.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionPrioritizerImpl implements SuggestionPrioritizer {
	private final RecommendationService recommendationService;

	public SuggestionPrioritizerImpl(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@Override
	public Uni<List<Suggestion>> prioritizeSuggestions(
			ReactivePersistenceContext em,
			PersonalInfo personalInfo,
			List<Suggestion> suggestions,
			Map<IngredientId, RecommendationComponent> primaryComponents
	) {
		return recommendationService.findRecommendationWeights(em, personalInfo)
				.map(weights -> orderSuggestionsAccordingToWeights(weights, primaryComponents, suggestions));
	}

	private List<Suggestion> orderSuggestionsAccordingToWeights(
			Map<Recommendation, BigDecimal> weights,
			Map<IngredientId, RecommendationComponent> primaryComponents,
			List<Suggestion> suggestions
	) {
		Comparator<Suggestion> comparator = Comparator.comparing(s -> weightOf(s, weights, primaryComponents));
		return suggestions.stream().sorted(comparator.reversed()).toList();
	}

	/**
	 * The weight of the master Recommendation behind a Suggestion; {@link Suggestion#getRecommendation()} is translated
	 * for display, so it is not the key of the weights. A Suggestion without a primary component weighs nothing.
	 */
	private BigDecimal weightOf(Suggestion suggestion, Map<Recommendation, BigDecimal> weights, Map<IngredientId, RecommendationComponent> primaryComponents) {
		if (!(suggestion.getTarget() instanceof AppliesTo.AppliesToIngredient(IngredientId ingredient))) {
			return BigDecimal.ZERO;
		}
		RecommendationComponent primaryComponent = primaryComponents.get(ingredient);
		return primaryComponent == null ? BigDecimal.ZERO : weights.getOrDefault(primaryComponent.getRecommendation(), BigDecimal.ZERO);
	}
}
