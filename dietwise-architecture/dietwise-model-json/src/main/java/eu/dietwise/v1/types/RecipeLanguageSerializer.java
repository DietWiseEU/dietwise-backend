package eu.dietwise.v1.types;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class RecipeLanguageSerializer extends ValueSerializer<RecipeLanguage> {
	@Override
	public void serialize(RecipeLanguage value, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
		jsonGenerator.writeString(value.getCode());
	}
}
