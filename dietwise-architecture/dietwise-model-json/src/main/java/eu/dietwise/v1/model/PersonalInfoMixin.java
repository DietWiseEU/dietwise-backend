package eu.dietwise.v1.model;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutablePersonalInfo.Builder.class)
public class PersonalInfoMixin {
}
