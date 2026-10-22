package eu.dietwise.services.v1.types;

/**
 * A change a user makes to the accepted/rejected counters of a Suggestion in their SuggestionStats.
 */
public enum SuggestionStatsTransition {
	INCREASE_ACCEPTED,
	DECREASE_ACCEPTED,
	INCREASE_REJECTED,
	DECREASE_REJECTED,
}
