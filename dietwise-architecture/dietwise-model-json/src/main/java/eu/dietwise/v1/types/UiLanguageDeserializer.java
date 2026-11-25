package eu.dietwise.v1.types;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class UiLanguageDeserializer extends ValueDeserializer<UiLanguage> {
	@Override
	public UiLanguage deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws JacksonException {
		return UiLanguage.fromCode(jsonParser.getValueAsString());
	}
}
