package eu.dietwise.dao.impl.suggestions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StagedLinkChangeTest {
	@ParameterizedTest(name = "present={0}, staged={1}, master={2} -> {3}")
	@CsvSource(nullValues = "null", value = {
			"true,  null,  true,  NONE",
			"false, null,  false, NONE",
			"true,  false, true,  DELETE",
			"false, true,  false, DELETE",
			"true,  null,  false, SEED",
			"false, null,  true,  SEED",
			"true,  true,  false, NONE",
			"false, false, true,  NONE"
	})
	void decidesTheWorkingCopyChange(boolean present, Boolean stagedPresence, boolean masterHasLink, StagedLinkChange expected) {
		assertThat(StagedLinkChange.of(present, stagedPresence, masterHasLink)).isEqualTo(expected);
	}
}
