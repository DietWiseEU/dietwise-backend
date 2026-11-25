package eu.dietwise.v1.types;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CountryDeserializer extends ValueDeserializer<Country> {
	@Override
	public Country deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
		return Country.fromCode2(jsonParser.getValueAsString());
	}
}
