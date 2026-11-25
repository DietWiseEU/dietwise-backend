package eu.dietwise.services.v1.extraction;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public interface RecipeJsonCoercionStrategy {
	String coerce(JsonNode node, ObjectMapper objectMapper);
}
