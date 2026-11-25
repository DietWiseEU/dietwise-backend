package eu.dietwise.v1.types;

import static org.assertj.core.api.Assertions.assertThat;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class RecipeLanguageTest {
	@Test
	void testSerialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var result = om.writeValueAsString(RecipeLanguage.EN);
		assertThat(result).isEqualTo("\"en\"");
	}

	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var result = om.readValue("\"en\"", RecipeLanguage.class);
		assertThat(result).isEqualTo(RecipeLanguage.EN);
	}
}
