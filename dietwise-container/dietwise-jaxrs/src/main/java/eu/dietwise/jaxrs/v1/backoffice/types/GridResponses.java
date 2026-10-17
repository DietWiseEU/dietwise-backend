package eu.dietwise.jaxrs.v1.backoffice.types;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * Shared conversions of the backoffice grid responses: per-language translation completeness as language name to state
 * name, and id sets as id string lists.
 */
final class GridResponses {
	private GridResponses() {
	}

	static Map<String, String> toStateNames(Map<RecipeLanguage, TranslationState> states) {
		return states.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
	}

	static List<String> toIdStrings(Set<UUID> ids) {
		return ids.stream().map(UUID::toString).toList();
	}
}
