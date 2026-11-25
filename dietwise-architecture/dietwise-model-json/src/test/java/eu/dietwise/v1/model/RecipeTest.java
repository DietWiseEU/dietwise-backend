package eu.dietwise.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.regex.Pattern;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class RecipeTest {
	@Test
	void testSerialization() throws Exception {
		var recipe = ImmutableRecipe.builder()
				.name("name")
				.addRecipeIngredients(ImmutableIngredient.builder().id(new GenericIngredientId(UUID.randomUUID().toString())).nameInRecipe("ingredient").build())
				.addRecipeInstructions("instruction")
				.build();
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var result = om.writeValueAsString(recipe);
		assertThat(result).matches(Pattern.compile(".*\"name\":\\s*\"name\".*"));
	}
}
