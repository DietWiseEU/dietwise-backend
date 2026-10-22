package eu.dietwise.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class UiLanguageDeserializer extends JsonDeserializer<UiLanguage> {
	@Override
	public UiLanguage deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JacksonException {
		return UiLanguage.fromCode(jsonParser.getValueAsString());
	}
}
