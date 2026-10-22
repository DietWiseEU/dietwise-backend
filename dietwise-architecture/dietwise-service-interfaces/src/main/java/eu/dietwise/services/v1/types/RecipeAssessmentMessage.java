package eu.dietwise.services.v1.types;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.model.ScoringData;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateId;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.SuggestionTemplateId;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage.class, name = "RECIPES"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage.class, name = "MORE_THAN_ONE_RECIPE"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage.class, name = "SUGGESTIONS"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.ScoringRecipeAssessmentMessage.class, name = "SCORING"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.RecipeAssessmentErrorMessage.class, name = "ERROR")
})
public sealed interface RecipeAssessmentMessage {
	record RecipeExtractionRecipeAssessmentMessage(
			List<RecipeAndDetectionType> recipes,
			String pageText
	) implements RecipeAssessmentMessage {
		public RecipeExtractionRecipeAssessmentMessage {
			recipes = List.copyOf(recipes);
		}
	}

	record MoreThanOneRecipesAssessmentMessage(int numberOfRecipes) implements RecipeAssessmentMessage {
	}

	/**
	 * The Suggestions for the recipe; {@code assessmentId} and {@code intervention} are present only when an Intervention
	 * was assigned to the assessment.
	 */
	record SuggestionsRecipeAssessmentMessage(
			List<Suggestion> suggestions,
			@Nullable @JsonInclude(JsonInclude.Include.NON_NULL) UUID assessmentId,
			@Nullable @JsonInclude(JsonInclude.Include.NON_NULL) Intervention intervention
	) implements RecipeAssessmentMessage, HasSuggestionTemplateIds {
		public SuggestionsRecipeAssessmentMessage {
			suggestions = List.copyOf(suggestions);
		}

		public SuggestionsRecipeAssessmentMessage(List<Suggestion> suggestions) {
			this(suggestions, null, null);
		}

		public SuggestionsRecipeAssessmentMessage withIntervention(UUID assessmentId, Intervention intervention) {
			return new SuggestionsRecipeAssessmentMessage(suggestions, assessmentId, intervention);
		}

		@Override
		public Set<SuggestionTemplateId> getSuggestionTemplateIds() {
			return suggestions.stream().map(HasSuggestionTemplateId::getId).collect(Collectors.toSet());
		}
	}

	record ScoringRecipeAssessmentMessage(ScoringData scoringData) implements RecipeAssessmentMessage {
	}

	record RecipeAssessmentErrorMessage(List<String> errors) implements RecipeAssessmentMessage {
		public RecipeAssessmentErrorMessage {
			errors = List.copyOf(errors);
		}
	}
}
