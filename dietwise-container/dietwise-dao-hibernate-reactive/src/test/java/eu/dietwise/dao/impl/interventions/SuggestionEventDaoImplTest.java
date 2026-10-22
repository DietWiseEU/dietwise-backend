package eu.dietwise.dao.impl.interventions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.SuggestionStatsTransition.DECREASE_ACCEPTED;
import static eu.dietwise.v1.types.SuggestionStatsTransition.INCREASE_ACCEPTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.interventions.SuggestionEventEntity;
import eu.dietwise.dao.jpa.interventions.SuggestionEventEntity_;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.ImmutableSuggestionEvent;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.services.model.interventions.SuggestionEvent;
import eu.dietwise.v1.types.SuggestionStatsTransition;
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
public class SuggestionEventDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final UUID USER_ID = UUID.fromString("509f2670-b04d-4f5e-8a2b-4a08f3c87062");
	private static final UUID USER_IDM_ID = UUID.fromString("0bc0df44-f71d-43ed-a388-38f04300f452");
	private static final UUID OTHER_USER_ID = UUID.fromString("7a1c2e9d-3f4b-4c5d-8e6f-0a1b2c3d4e5f");
	private static final UUID OTHER_USER_IDM_ID = UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f");

	private static final SuggestionTemplateId SUGGESTION_ID =
			new GenericSuggestionTemplateId("90984ed6-5e6b-4381-ac0d-92b6dd3cf503");

	private static final String APPLICATION_ID = "recipewatch";
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 22, 12, 30, 15);

	private static final UUID ASSESSMENT_ID = UUID.fromString("3e0c7a52-1b6d-4f8e-9a2c-5d4b3a2f1e0d");
	private static final UUID OTHER_USER_ASSESSMENT_ID = UUID.fromString("8f2d4b6a-9c1e-4a3f-b5d7-e9f1a3c5b7d9");

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
	void insertedEventsAreAppendedAndNeverMerged(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var assignmentDao = new InterventionAssignmentDaoImpl();
		var sut = new SuggestionEventDaoImpl();
		factory.withTransaction(tx -> tx.persistAll(user(USER_ID, USER_IDM_ID), user(OTHER_USER_ID, OTHER_USER_IDM_ID))
						.chain(() -> assignmentDao.insert(tx, assignment(ASSESSMENT_ID, USER_ID)))
						.chain(() -> assignmentDao.insert(tx, assignment(OTHER_USER_ASSESSMENT_ID, OTHER_USER_ID))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.insert(tx, event(ASSESSMENT_ID, INCREASE_ACCEPTED))
						.chain(() -> sut.insert(tx, event(ASSESSMENT_ID, DECREASE_ACCEPTED)))
						.chain(() -> sut.insert(tx, event(ASSESSMENT_ID, INCREASE_ACCEPTED)))
						.chain(() -> sut.insert(tx, event(OTHER_USER_ASSESSMENT_ID, INCREASE_ACCEPTED))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(findEvents(factory, ASSESSMENT_ID))
				.extracting(SuggestionEventEntity::getSuggestionTemplateId, SuggestionEventEntity::getAction, SuggestionEventEntity::getCreatedAt)
				.containsExactlyInAnyOrder(
						tuple(SUGGESTION_ID.asUuid(), INCREASE_ACCEPTED, CREATED_AT),
						tuple(SUGGESTION_ID.asUuid(), DECREASE_ACCEPTED, CREATED_AT),
						tuple(SUGGESTION_ID.asUuid(), INCREASE_ACCEPTED, CREATED_AT)
				);
	}

	@Test
	@Order(2)
	void anEventRequiresAnExistingAssignment(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new SuggestionEventDaoImpl();

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.insert(tx, event(UUID.fromString("00000000-0000-0000-0000-000000000000"), INCREASE_ACCEPTED)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).hasMessageContaining("FK_SUGG_EVENT__ASSESSMENT_ID__INTV_ASGN__ASSESSMENT_ID".toLowerCase());
	}

	@Test
	@Order(3)
	void deleteByUserDeletesOnlyTheEventsOfTheUser(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new SuggestionEventDaoImpl();

		factory.withTransaction(tx -> sut.deleteByUser(tx, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(findEvents(factory, ASSESSMENT_ID)).isEmpty();
		assertThat(findEvents(factory, OTHER_USER_ASSESSMENT_ID)).hasSize(1);
	}

	private static List<SuggestionEventEntity> findEvents(ReactivePersistenceContextFactoryImpl factory, UUID assessmentId) {
		return factory.withoutTransaction(em -> {
			var cb = em.getCriteriaBuilder();
			var q = cb.createQuery(SuggestionEventEntity.class);
			var root = q.from(SuggestionEventEntity.class);
			q.where(cb.equal(root.get(SuggestionEventEntity_.interventionAssignmentAssessmentId), assessmentId));
			return em.createQuery(q).getResultList();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static SuggestionEvent event(UUID assessmentId, SuggestionStatsTransition action) {
		return ImmutableSuggestionEvent.builder()
				.assessmentId(assessmentId)
				.suggestionTemplateId(SUGGESTION_ID)
				.action(action)
				.createdAt(CREATED_AT)
				.build();
	}

	private static InterventionAssignment assignment(UUID assessmentId, UUID userId) {
		return ImmutableInterventionAssignment.builder()
				.assessmentId(assessmentId)
				.userId(userId)
				.applicationId(APPLICATION_ID)
				.interventionId(2)
				.uiLang(UiLanguage.EL)
				.recipeUrl("https://example.test/recipe")
				.createdAt(CREATED_AT)
				.suggestionTemplateIds(Set.of(SUGGESTION_ID))
				.build();
	}

	private static UserEntity user(UUID id, UUID idmId) {
		var user = new UserEntity();
		user.setId(id);
		user.setIdmId(idmId.toString());
		return user;
	}
}
