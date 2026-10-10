package eu.dietwise.jaxrs.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.RuleField;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import org.junit.jupiter.api.Test;

class RuleResponseTest {
	private static final Rule RULE = ImmutableRule.builder()
			.id(new GenericRuleId("00000000-0000-0000-0000-000000000001"))
			.recommendation(new RecommendationImpl("Decrease red meat"))
			.triggerIngredient(new TriggerIngredientImpl("Beef"))
			.build();

	@Test
	void changedFieldsAreListedInDeclarationOrder() {
		var changedFields = new LinkedHashSet<>(List.of(RuleField.SUGGESTION_TEMPLATES, RuleField.TRIGGER_INGREDIENT, RuleField.RATIONALE));
		var staged = new StagedRule(RULE, UUID.randomUUID(), null, RuleChangeState.CHANGED, changedFields, Map.of(), Map.of(), Map.of(), 0L);

		assertThat(RuleResponse.from(staged).changedFields()).containsExactly("RATIONALE", "TRIGGER_INGREDIENT", "SUGGESTION_TEMPLATES");
	}
}
