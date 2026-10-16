package eu.dietwise.dao.impl.suggestions;

/**
 * What staging a scoring-component link to an absolute target presence does to the link's sparse Working Copy row. A
 * row exists only while the effective link differs from published master, so staging the presence master already has
 * removes the row, and staging a presence the row already carries changes nothing.
 */
enum StagedLinkChange {
	/** The Working Copy already reflects the target presence. */
	NONE,
	/** Insert a Working Copy row carrying the target presence. */
	SEED,
	/** Overwrite the presence carried by the existing Working Copy row. */
	UPDATE,
	/** Remove the existing Working Copy row; the target presence is the one in master. */
	DELETE;

	/**
	 * @param present        The target effective presence of the link
	 * @param stagedPresence The presence carried by the existing Working Copy row, {@code null} when there is none
	 * @param masterHasLink  Whether published master has the link
	 */
	static StagedLinkChange of(boolean present, Boolean stagedPresence, boolean masterHasLink) {
		if (present == masterHasLink) {
			return stagedPresence == null ? NONE : DELETE;
		} else if (stagedPresence == null) {
			return SEED;
		} else {
			return stagedPresence == present ? NONE : UPDATE;
		}
	}
}
