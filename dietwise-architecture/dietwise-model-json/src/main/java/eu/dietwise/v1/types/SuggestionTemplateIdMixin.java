package eu.dietwise.v1.types;

import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = GenericSuggestionTemplateId.class)
public abstract class SuggestionTemplateIdMixin {
}
