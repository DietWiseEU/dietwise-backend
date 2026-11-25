package eu.dietwise.v1.model;

import static eu.dietwise.v1.types.RecipeLanguage.EN;
import static eu.dietwise.v1.types.UiLanguage.EL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

public class RecipeExtractionAndAssessmentParamTest {
	@Test
	void testDeserializationWithUiLang() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var json = """
				{
					"url": "https://example.test/recipe",
					"lang": "en",
					"uiLang": "el"
				}
				""";

		var result = om.readValue(json, RecipeExtractionAndAssessmentParam.class);

		assertThat(result.getUrl()).isEqualTo("https://example.test/recipe");
		assertThat(result.getLang()).isEqualTo(EN);
		assertThat(result.getUiLang()).isEqualTo(EL);
	}

	@Test
	void testDeserializationWithoutUiLang() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var json = """
				{
					"url": "https://example.test/recipe",
					"lang": "en"
				}
				""";

		var result = om.readValue(json, RecipeExtractionAndAssessmentParam.class);

		assertThat(result.getUiLang()).isNull();
	}

	@Test
	void testDeserializationRejectsUnsupportedUiLang() {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var json = """
				{
					"url": "https://example.test/recipe",
					"lang": "en",
					"uiLang": "de"
				}
				""";

		assertThatThrownBy(() -> om.readValue(json, RecipeExtractionAndAssessmentParam.class))
				.isInstanceOf(DatabindException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testDeserializationRejectsBlankUiLang() {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var json = """
				{
					"url": "https://example.test/recipe",
					"lang": "en",
					"uiLang": ""
				}
				""";

		assertThatThrownBy(() -> om.readValue(json, RecipeExtractionAndAssessmentParam.class))
				.isInstanceOf(DatabindException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}
}
