package eu.dietwise.tests;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class BreakerTestProfile implements QuarkusTestProfile {
	@Override
	public Map<String, String> getConfigOverrides() {
		return Map.of(
				"quarkus.liquibase.migrate-at-start", "false",
				"quarkus.oidc.tenant-enabled", "false",
				// Syntactically valid URLs so the PG client startup parse succeeds. No connection is actually opened:
				// Hibernate starts offline (no database version check or JDBC metadata query on startup) and the test
				// path mocks every DAO.
				"quarkus.datasource.reactive.url", "postgresql://localhost:5432/dietwise-test",
				"quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:5432/dietwise-test",
				"quarkus.datasource.username", "test",
				"quarkus.datasource.password", "test",
				"quarkus.hibernate-orm.database.start-offline", "true",
				// Just to satisfy the cthonic deities
				"dietwise.git-hash", "abc"
		);
	}
}
