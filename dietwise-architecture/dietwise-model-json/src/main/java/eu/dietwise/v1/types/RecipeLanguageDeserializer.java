package eu.dietwise.v1.types;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class RecipeLanguageDeserializer extends ValueDeserializer<RecipeLanguage> {
	@Override
	public RecipeLanguage deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws JacksonException {
		return RecipeLanguage.fromCode(jsonParser.getValueAsString());
	}
}
