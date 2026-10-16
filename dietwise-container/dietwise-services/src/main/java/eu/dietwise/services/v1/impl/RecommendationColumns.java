package eu.dietwise.services.v1.impl;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.v1.types.TypeOfRecommendation;

/**
 * Shared helpers of the backoffice grids whose columns are Recommendations: the columns in display order, and the
 * restriction of a row's links to those columns.
 */
final class RecommendationColumns {
	/** The grid shows the ENCOURAGED columns first, then the LIMITED ones; each group keeps the order of the Recommendations. */
	private static final List<TypeOfRecommendation> COLUMN_GROUP_ORDER = List.of(TypeOfRecommendation.ENCOURAGED, TypeOfRecommendation.LIMITED);

	private RecommendationColumns() {
	}

	static List<RecommendationColumn> inDisplayOrder(List<BackofficeRecommendation> recommendations) {
		return recommendations.stream()
				.map(recommendation -> new RecommendationColumn(recommendation.id(), recommendation.componentForScoring(), recommendation.typeOfRecommendation()))
				.sorted(Comparator.comparingInt(column -> COLUMN_GROUP_ORDER.indexOf(column.typeOfRecommendation())))
				.toList();
	}

	static Set<UUID> ids(List<RecommendationColumn> columns) {
		return columns.stream().map(RecommendationColumn::id).collect(Collectors.toSet());
	}

	static Set<UUID> retainColumns(Set<UUID> ids, Set<UUID> columnIds) {
		if (ids == null) {
			return Set.of();
		}
		return ids.stream().filter(columnIds::contains).collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
