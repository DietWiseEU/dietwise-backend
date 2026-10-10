package eu.dietwise.v1.types.impl;

import java.util.Objects;

import eu.dietwise.v1.types.RecommendationComponentName;

public final class RecommendationComponentNameImpl implements RecommendationComponentName {
	private final String representation;

	public RecommendationComponentNameImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RecommendationComponentName that)) return false;
		return Objects.equals(representation, that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "RecommendationComponentNameImpl(" + representation + ")";
	}
}
