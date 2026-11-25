package eu.dietwise.v1.types;

import eu.dietwise.common.types.RepresentableAsString;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class RepresentableAsStringKeySerializer extends ValueSerializer<RepresentableAsString> {
	@Override
	public void serialize(RepresentableAsString representableAsString, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
		// Remember: representableAsString is never null, as per the specs of Jackson
		jsonGenerator.writeName(representableAsString.asString());
	}
}
