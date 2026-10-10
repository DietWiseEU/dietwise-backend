package eu.dietwise.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import org.junit.jupiter.api.Test;

public class RecipeTest {
	@Test
	void testSerialization() throws Exception {
		var recipe = ImmutableRecipe.builder()
				.name("name")
				.addRecipeIngredients(ImmutableIngredient.builder().id(new GenericIngredientId(UUID.randomUUID().toString())).nameInRecipe("ingredient").build())
				.addRecipeInstructions("instruction")
				.build();
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		om.registerModule(new Jdk8Module()); // at runtime Quarkus provides this
		var result = om.writeValueAsString(recipe);
		assertThat(result).matches(Pattern.compile(".*\"name\":\\s*\"name\".*"));
	}
}
