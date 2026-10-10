package eu.dietwise.jaxrs.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import org.junit.jupiter.api.Test;

class SuggestionTemplateResponseTest {
	private static final SuggestionTemplate TEMPLATE = ImmutableSuggestionTemplate.builder()
			.id(new GenericSuggestionTemplateId("00000000-0000-0000-0000-000000000001"))
			.alternative(new AlternativeIngredientImpl("Lentils"))
			.build();

	@Test
	void changedFieldsAreListedInDeclarationOrder() {
		var changedFields = new LinkedHashSet<>(List.of(SuggestionTemplateField.TECHNIQUE_NOTES, SuggestionTemplateField.EQUIVALENCE, SuggestionTemplateField.RESTRICTION));
		var staged = new StagedSuggestionTemplate(TEMPLATE, null, changedFields, Map.of(), Map.of(), true, false, true, 0L);

		assertThat(SuggestionTemplateResponse.from(staged).changedFields()).containsExactly("RESTRICTION", "EQUIVALENCE", "TECHNIQUE_NOTES");
	}
}
