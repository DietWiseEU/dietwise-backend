package eu.dietwise.tools.publish;

import java.util.Map;

import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Applies the application's Liquibase changelog to a fresh test database. Such a database is never the one the publish
 * changesets in the changelog were generated from, so it skips their Working Copy check.
 */
final class ApplicationChangelog {

	static final Map<String, String> SKIP_WORKING_COPY_CHECK =
			Map.of(ChangelogRenderer.WORKING_COPY_CHECK_PARAMETER, ChangelogRenderer.WORKING_COPY_CHECK_SKIP);

	private ApplicationChangelog() {
	}

	static void applyTo(PostgreSQLContainer postgres) throws Exception {
		LiquibaseExtension.executeUpdate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(),
				"changelog.xml", null, SKIP_WORKING_COPY_CHECK);
	}
}
