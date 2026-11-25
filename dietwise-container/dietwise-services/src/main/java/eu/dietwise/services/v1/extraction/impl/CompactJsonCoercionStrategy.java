package eu.dietwise.services.v1.extraction.impl;

import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class CompactJsonCoercionStrategy implements RecipeJsonCoercionStrategy {
	@Override
	public String coerce(JsonNode node, ObjectMapper objectMapper) {
		if (node == null || node.isNull()) return "";
		if (node.isString()) return node.stringValue();
		if (node.isNumber() || node.isBoolean()) return node.asString();
		try {
			return objectMapper.writeValueAsString(node);
		} catch (JacksonException e) {
			return node.toString();
		}
	}
}
