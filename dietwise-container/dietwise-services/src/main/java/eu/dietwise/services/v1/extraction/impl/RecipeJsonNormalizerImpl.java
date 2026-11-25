package eu.dietwise.services.v1.extraction.impl;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import eu.dietwise.services.v1.extraction.RecipeJsonNormalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ApplicationScoped
public class RecipeJsonNormalizerImpl implements RecipeJsonNormalizer {
	private final ObjectMapper objectMapper;
	private final RecipeJsonCoercionStrategy coercionStrategy;

	public RecipeJsonNormalizerImpl(ObjectMapper objectMapper, RecipeJsonCoercionStrategy coercionStrategy) {
		this.objectMapper = objectMapper;
		this.coercionStrategy = coercionStrategy;
	}

	@Override
	public String normalize(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			if (!root.isObject()) return json;
			ObjectNode obj = (ObjectNode) root;
			normalizeArrayField(obj, "recipeIngredients");
			normalizeArrayField(obj, "recipeInstructions");
			return objectMapper.writeValueAsString(obj);
		} catch (JacksonException e) {
			return json;
		}
	}

	private void normalizeArrayField(ObjectNode obj, String fieldName) {
		JsonNode node = obj.get(fieldName);
		if (node == null || node.isNull()) return;

		if (node.isArray()) {
			List<String> normalized = new ArrayList<>();
			for (JsonNode item : node) {
				normalized.add(coerceToString(item));
			}
			ArrayNode array = objectMapper.createArrayNode();
			for (String value : normalized) array.add(value);
			obj.set(fieldName, array);
		} else {
			ArrayNode array = objectMapper.createArrayNode();
			array.add(coerceToString(node));
			obj.set(fieldName, array);
		}
	}

	private String coerceToString(JsonNode node) {
		String value = coercionStrategy.coerce(node, objectMapper);
		return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
	}
}
