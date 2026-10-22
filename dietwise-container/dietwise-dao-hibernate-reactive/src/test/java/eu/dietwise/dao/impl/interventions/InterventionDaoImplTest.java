package eu.dietwise.dao.impl.interventions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.UiLanguage.EL;
import static eu.dietwise.v1.types.UiLanguage.EN;
import static eu.dietwise.v1.types.UiLanguage.LT;
import static eu.dietwise.v1.types.UiLanguage.NL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.interventions.InterventionEntity;
import eu.dietwise.services.model.interventions.TranslatedIntervention;
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
public class InterventionDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final int DEACTIVATED_INTERVENTION_ID = 3;
	private static final int NEW_INTERVENTION_ID = 6;

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
	void findActiveReturnsTheSeededInterventionsWithTheirTexts(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new InterventionDaoImpl();

		List<TranslatedIntervention> result = factory.withoutTransaction(sut::findActive)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result).extracting(TranslatedIntervention::getId).containsExactly(0, 1, 2, 3, 4, 5);
		assertThat(result.getFirst().getTexts()).isEmpty();
		assertThat(result.get(2).getTexts()).isEqualTo(Map.of(
				EN, "Healthiness guaranteed.",
				NL, "Gegarandeerd gezonder.",
				EL, "Εγγυημένη υγιεινή επιλογή.",
				LT, "Garantuotai sveikiau."
		));
		assertThat(result.get(3).getTexts()).containsEntry(EN, "It’s a little different, but give it a shot!");
	}

	@Test
	@Order(2)
	void findActiveOmitsInactiveInterventions(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new InterventionDaoImpl();
		factory.withTransaction(tx -> tx.find(InterventionEntity.class, DEACTIVATED_INTERVENTION_ID)
				.invoke(intervention -> intervention.setActive(false))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		List<TranslatedIntervention> result = factory.withoutTransaction(sut::findActive)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result).extracting(TranslatedIntervention::getId).containsExactly(0, 1, 2, 4, 5);
	}

	@Test
	@Order(3)
	void anInterventionOtherThanTheNoInterventionArmRequiresAnEnglishText(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var intervention = new InterventionEntity();
		intervention.setId(NEW_INTERVENTION_ID);
		intervention.setActive(true);
		intervention.setTextNl("Alleen in het Nederlands.");

		assertThatThrownBy(() -> factory.withTransaction(tx -> tx.persist(intervention))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).hasMessageContaining("CK_DW_INTERVENTION__TEXT_EN".toLowerCase());
	}
}
