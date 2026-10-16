package eu.dietwise.jaxrs.v1;

import eu.dietwise.services.v1.types.RecommendationColumn;

/**
 * One column of a backoffice grid whose columns are Recommendations: a Recommendation's id, the component for scoring
 * shown in its header and its type of recommendation ({@code ENCOURAGED} or {@code LIMITED}).
 */
public record RecommendationColumnResponse(String id, String componentForScoring, String typeOfRecommendation) {
	static RecommendationColumnResponse from(RecommendationColumn column) {
		return new RecommendationColumnResponse(column.id().toString(), column.componentForScoring(), column.typeOfRecommendation().name());
	}
}
