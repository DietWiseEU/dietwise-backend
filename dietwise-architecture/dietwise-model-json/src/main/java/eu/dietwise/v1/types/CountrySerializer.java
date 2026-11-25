package eu.dietwise.v1.types;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class CountrySerializer extends ValueSerializer<Country> {
	@Override
	public void serialize(Country country, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
		jsonGenerator.writeString(country.getCode2());
	}
}
