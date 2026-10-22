package eu.dietwise.dao.impl.interventions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.UiLanguage;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class InterventionAssignmentDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final UUID USER_ID = UUID.fromString("509f2670-b04d-4f5e-8a2b-4a08f3c87062");
	private static final UUID USER_IDM_ID = UUID.fromString("0bc0df44-f71d-43ed-a388-38f04300f452");
	private static final UUID OTHER_USER_ID = UUID.fromString("7a1c2e9d-3f4b-4c5d-8e6f-0a1b2c3d4e5f");
	private static final UUID OTHER_USER_IDM_ID = UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f");

	private static final SuggestionTemplateId SUGGESTION_ID_1 =
			new GenericSuggestionTemplateId("90984ed6-5e6b-4381-ac0d-92b6dd3cf503");
	private static final SuggestionTemplateId SUGGESTION_ID_2 =
			new GenericSuggestionTemplateId("ce09b981-0d78-499a-9430-42a324d665a4");

	private static final String APPLICATION_ID = "recipewatch";
	private static final String RECIPE_URL = "https://example.test/recipe";
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 22, 12, 30, 15);

	private static final InterventionAssignment ASSIGNMENT = assignment(UUID.fromString("3e0c7a52-1b6d-4f8e-9a2c-5d4b3a2f1e0d"), USER_ID);
	private static final InterventionAssignment OTHER_USER_ASSIGNMENT = assignment(UUID.fromString("8f2d4b6a-9c1e-4a3f-b5d7-e9f1a3c5b7d9"), OTHER_USER_ID);

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void insertedAssignmentIsFoundWithItsSuggestions(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new InterventionAssignmentDaoImpl();
		factory.withTransaction(tx -> tx.persistAll(user(USER_ID, USER_IDM_ID), user(OTHER_USER_ID, OTHER_USER_IDM_ID)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.insert(tx, ASSIGNMENT).chain(() -> sut.insert(tx, OTHER_USER_ASSIGNMENT)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		Optional<InterventionAssignment> result = factory.withoutTransaction(em -> sut.findById(em, ASSIGNMENT.getAssessmentId()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result).contains(ASSIGNMENT);
	}

	@Test
	@Order(2)
	void findByIdOfUnknownAssessmentIsEmpty(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new InterventionAssignmentDaoImpl();

		Optional<InterventionAssignment> result = factory.withoutTransaction(em -> sut.findById(em, UUID.fromString("00000000-0000-0000-0000-000000000000")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result).isEmpty();
	}

	@Test
	@Order(3)
	void deleteByUserDeletesOnlyTheAssignmentsOfTheUser(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new InterventionAssignmentDaoImpl();

		factory.withTransaction(tx -> sut.deleteByUser(tx, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(factory.withoutTransaction(em -> sut.findById(em, ASSIGNMENT.getAssessmentId()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))).isEmpty();
		assertThat(factory.withoutTransaction(em -> sut.findById(em, OTHER_USER_ASSIGNMENT.getAssessmentId()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))).contains(OTHER_USER_ASSIGNMENT);
	}

	private static InterventionAssignment assignment(UUID assessmentId, UUID userId) {
		return ImmutableInterventionAssignment.builder()
				.assessmentId(assessmentId)
				.userId(userId)
				.applicationId(APPLICATION_ID)
				.interventionId(2)
				.uiLang(UiLanguage.EL)
				.recipeUrl(RECIPE_URL)
				.createdAt(CREATED_AT)
				.suggestionTemplateIds(Set.of(SUGGESTION_ID_1, SUGGESTION_ID_2))
				.build();
	}

	private static UserEntity user(UUID id, UUID idmId) {
		var user = new UserEntity();
		user.setId(id);
		user.setIdmId(idmId.toString());
		return user;
	}
}
