package eu.dietwise.services.v1.extraction.impl;

import java.util.ArrayList;
import java.util.List;

import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JoinValuesCoercionStrategy implements RecipeJsonCoercionStrategy {
	@Override
	public String coerce(JsonNode node, ObjectMapper objectMapper) {
		if (node == null || node.isNull()) return "";
		if (node.isString()) return node.stringValue();
		if (node.isNumber() || node.isBoolean()) return node.asString();
		List<String> parts = new ArrayList<>();
		collectText(node, parts);
		return String.join(" ", parts);
	}

	private void collectText(JsonNode node, List<String> parts) {
		if (node == null || node.isNull()) return;
		if (node.isString()) {
			parts.add(node.stringValue());
			return;
		}
		if (node.isNumber() || node.isBoolean()) {
			parts.add(node.asString());
			return;
		}
		if (node.isArray()) {
			for (JsonNode item : node) collectText(item, parts);
			return;
		}
		if (node.isObject()) {
			node.properties().forEach(entry -> collectText(entry.getValue(), parts));
		}
	}
}
