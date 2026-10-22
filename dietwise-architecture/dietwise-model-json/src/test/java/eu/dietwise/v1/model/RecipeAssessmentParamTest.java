package eu.dietwise.v1.model;

import static eu.dietwise.v1.types.Country.GREECE;
import static eu.dietwise.v1.types.RecipeLanguage.EL;
import static eu.dietwise.v1.types.RecipeLanguage.EN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;

public class RecipeAssessmentParamTest {
	@Test
	void testDeserializationWithCountryOverride() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"url": "https://example.test/recipe",
					"pageContent": "Recipe text",
					"lang": "en",
					"countryOverride": "GR"
				}
				""";

		var result = om.readValue(json, RecipeAssessmentParam.class);

		assertThat(result.getUrl()).isEqualTo("https://example.test/recipe");
		assertThat(result.getPageContent()).isEqualTo("Recipe text");
		assertThat(result.getLang()).isEqualTo(EN);
		assertThat(result.getCountryOverride()).isEqualTo(GREECE);
	}

	@Test
	void testDeserializationWithUiLang() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"url": "https://example.test/recipe",
					"pageContent": "Recipe text",
					"lang": "en",
					"uiLang": "el"
				}
				""";

		var result = om.readValue(json, RecipeAssessmentParam.class);

		assertThat(result.getUiLang()).isEqualTo(EL);
	}

	@Test
	void testDeserializationWithoutUiLang() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"url": "https://example.test/recipe",
					"pageContent": "Recipe text",
					"lang": "en"
				}
				""";

		var result = om.readValue(json, RecipeAssessmentParam.class);

		assertThat(result.getUiLang()).isNull();
	}

	@Test
	void testDeserializationRejectsUnsupportedUiLang() {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"url": "https://example.test/recipe",
					"pageContent": "Recipe text",
					"lang": "en",
					"uiLang": "de"
				}
				""";

		assertThatThrownBy(() -> om.readValue(json, RecipeAssessmentParam.class))
				.isInstanceOf(JsonMappingException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}
}
