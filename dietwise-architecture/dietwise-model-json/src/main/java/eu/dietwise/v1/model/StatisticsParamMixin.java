package eu.dietwise.v1.model;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableStatisticsParam.Builder.class)
public abstract class StatisticsParamMixin {
}
