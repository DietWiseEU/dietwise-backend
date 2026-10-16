package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.CriteriaDelete;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity_;
import eu.dietwise.services.model.suggestions.BackofficeTriggerIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.TypeOfRecommendation;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
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
class TriggerIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final String NEW_TRIGGER_INGREDIENT_NAME = "Quinoa flour";
	private static final String MASTER_NAME = "Beef";
	private static final String EDITED_NAME = "Bovine";
	private static final String EDITED_EXPLANATION = "Red meat; the centrepiece protein.";
	/** As long as DW_TRIGGER_INGREDIENT.explanation_for_llm holds, which its Working Copy mirror must match. */
	private static final String LONGEST_EXPLANATION = "e".repeat(300);

	private static final UUID TRANSLATION_TI_ID = UUID.fromString("b1c2d3e4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID TRANSLATION_COLLAPSE_TI_ID = UUID.fromString("b1c2d3e4-0002-4f5a-8b9c-0d1e2f3a0002");
	private static final UUID TRANSLATION_BUMP_TI_ID = UUID.fromString("b1c2d3e4-0003-4f5a-8b9c-0d1e2f3a0003");
	private static final UUID TRANSLATION_REVERT_TI_ID = UUID.fromString("b1c2d3e4-0004-4f5a-8b9c-0d1e2f3a0004");
	private static final UUID TRANSLATION_REVERT_STALE_TI_ID = UUID.fromString("b1c2d3e4-0005-4f5a-8b9c-0d1e2f3a0005");
	private static final UUID TRANSLATION_NULL_TI_ID = UUID.fromString("b1c2d3e4-0006-4f5a-8b9c-0d1e2f3a0006");
	private static final UUID REVERT_TI_ID = UUID.fromString("b1c2d3e4-0007-4f5a-8b9c-0d1e2f3a0007");
	private static final UUID REVERT_STALE_TI_ID = UUID.fromString("b1c2d3e4-0008-4f5a-8b9c-0d1e2f3a0008");
	private static final UUID REVERT_NOOP_TI_ID = UUID.fromString("b1c2d3e4-0009-4f5a-8b9c-0d1e2f3a0009");
	private static final String MASTER_EL_NAME = "Φακές";
	private static final String MASTER_EL_EXPLANATION = "Όσπριο πλούσιο σε πρωτεΐνη.";
	private static final String EDITED_EL_NAME = "Φακές (αναθ.)";
	private static final String EDITED_EL_EXPLANATION = "Αναθεωρημένη εξήγηση.";
	private static final String STAGED_NL_NAME = "Linzen";
	private static final String STAGED_NL_EXPLANATION = "Eiwitrijke peulvrucht.";
	private static final String RESTAGED_NL_NAME = "Linzen herzien";
	private static final String RESTAGED_NL_EXPLANATION = "Herziene uitleg.";
	private static final UUID DECREASE_RED_MEAT_RECOMMENDATION_ID = UUID.fromString("9977a713-0608-4f0c-9933-f7788b4f0225");
	private static final UUID GRID_UNCHANGED_TI_ID = UUID.fromString("b1c2d3e4-0030-4f5a-8b9c-0d1e2f3a0030");
	private static final UUID GRID_EDITED_TI_ID = UUID.fromString("b1c2d3e4-0031-4f5a-8b9c-0d1e2f3a0031");
	private static final UUID TOGGLE_REMOVE_TI_ID = UUID.fromString("b1c2d3e4-0033-4f5a-8b9c-0d1e2f3a0033");
	private static final UUID TOGGLE_ADD_TI_ID = UUID.fromString("b1c2d3e4-0034-4f5a-8b9c-0d1e2f3a0034");
	private static final UUID REC_C_ID = UUID.fromString("b1c2d3e4-00a2-4f5a-8b9c-0d1e2f3a00a2");
	private static final UUID REC_D_ID = UUID.fromString("b1c2d3e4-00a3-4f5a-8b9c-0d1e2f3a00a3");
	private static final String DECREASE_RED_MEAT_RECOMMENDATION_NAME = "Decrease red meat";

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
	void testFindAllReturnsBaseTriggerIngredientWhenTranslationIsMissing(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var cb = tx.getCriteriaBuilder();
			CriteriaDelete<TriggerIngredientTranslationEntity> delete = cb.createCriteriaDelete(TriggerIngredientTranslationEntity.class);
			var root = delete.from(TriggerIngredientTranslationEntity.class);
			delete.where(cb.and(
					cb.equal(root.get(TriggerIngredientTranslationEntity_.lang), "NL"),
					cb.equal(root.get(TriggerIngredientTranslationEntity_.name), "Rundvlees")
			));
			return tx.createDelete(delete).execute();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Beef");
		assertThat(triggerIngredient.getExplanationForLlm()).isEmpty();
	}

	@Test
	@Order(2)
	void testFindAllReturnsLocalizedTriggerIngredientWhenTranslationExists(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> tx.find(TriggerIngredientEntity.class, TRIGGER_INGREDIENT_ID)
						.flatMap(triggerIngredient -> {
							var translation = new TriggerIngredientTranslationEntity();
							translation.setTriggerIngredient(triggerIngredient);
							translation.setLang(RecipeLanguage.NL);
							translation.setName("Rundvlees");
							translation.setExplanationForLlm("NL trigger");
							return tx.persist(translation);
						}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Rundvlees");
		assertThat(triggerIngredient.getExplanationForLlm()).contains("NL trigger");
	}

	@Test
	@Order(3)
	void testListOptionsReturnsMasterEntries(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"));
	}

	@Test
	@Order(4)
	void testCreateTriggerIngredientStagesAMirrorRowVisibleInListOptions(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, NEW_TRIGGER_INGREDIENT_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(options).contains(new ReferenceOption(newId, NEW_TRIGGER_INGREDIENT_NAME));
		assertThat(options).contains(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"));
	}

	@Test
	@Order(5)
	void testFindEditableByIdReturnsMasterDetailsWhenUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
	}

	@Test
	@Order(6)
	void testEditTriggerIngredientSeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.explanationForLlm()).isEqualTo(EDITED_EXPLANATION);
		assertThat(details.version()).isEqualTo(1L);
		assertThat(details.published()).isTrue();

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).containsEntry(TRIGGER_INGREDIENT_ID, EDITED_NAME);
	}

	@Test
	@Order(7)
	void testEditTriggerIngredientBackToMasterValuesCollapsesTheMirrorRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, MASTER_NAME, null, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(TRIGGER_INGREDIENT_ID);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(8)
	void testEditTriggerIngredientRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, "Zebu", "stale", 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(9)
	void testEditWorkingCopyOnlyTriggerIngredientBumpsItsMirror(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, "Tempeh"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, newId, "Tempeh strips", "Fermented soy cake.", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Tempeh strips");
		assertThat(details.explanationForLlm()).isEqualTo("Fermented soy cake.");
		assertThat(details.version()).isEqualTo(2L);
		assertThat(details.published()).isFalse();
	}

	@Test
	@Order(10)
	void testFindEditableByIdRejectsUnknownTriggerIngredient(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		assertThatThrownBy(() -> factory.withoutTransaction(em -> sut.findEditableById(em, UUID.randomUUID()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	@Order(11)
	void testFindTranslationsForEditOverlaysStagedOnMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_TI_ID, "Lentils (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));
		assertThat(forEdit.get(RecipeLanguage.LT)).isEqualTo(new ReferenceDetails(null, null, 0L, false));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_TI_ID).full()).containsExactlyInAnyOrder(RecipeLanguage.EL, RecipeLanguage.NL);
		assertThat(langs.get(TRANSLATION_TI_ID).staged()).containsExactly(RecipeLanguage.NL);
	}

	@Test
	@Order(12)
	void testStageTranslationSeedsThenCollapsesToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_COLLAPSE_TI_ID, "Chickpeas (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TRANSLATION_COLLAPSE_TI_ID).staged()).containsExactly(RecipeLanguage.EL);

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_COLLAPSE_TI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_COLLAPSE_TI_ID).full()).containsExactly(RecipeLanguage.EL);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_COLLAPSE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
	}

	@Test
	@Order(13)
	void testStageTranslationBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_BUMP_TI_ID, "Tofu (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_BUMP_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 2L, false));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, "Stale.", "x", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(14)
	void testRevertTranslationRemovesStagedRowRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_REVERT_TI_ID, "Seitan (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_REVERT_TI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_REVERT_TI_ID).full()).containsExactly(RecipeLanguage.EL);
	}

	@Test
	@Order(15)
	void testRevertTranslationRejectsStaleBaseVersionLeavingItIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_REVERT_STALE_TI_ID, "Edamame (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterRevert = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(16)
	void testStageNullTranslationWithNoMasterIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_NULL_TI_ID, "Quinoa (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_NULL_TI_ID, RecipeLanguage.EL, null, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs).doesNotContainKey(TRANSLATION_NULL_TI_ID);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_NULL_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(17)
	void testRevertTriggerIngredientRemovesStagedEditRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, REVERT_TI_ID, "Black beans"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, REVERT_TI_ID, "Black beans (edited)", "Legume protein.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTriggerIngredient(tx, REVERT_TI_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Black beans");
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(REVERT_TI_ID);
	}

	@Test
	@Order(18)
	void testRevertTriggerIngredientRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, REVERT_STALE_TI_ID, "Pinto beans"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, REVERT_STALE_TI_ID, "Pinto beans (edited)", "Legume protein.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTriggerIngredient(tx, REVERT_STALE_TI_ID, 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_STALE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Pinto beans (edited)");
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(19)
	void testRevertTriggerIngredientIsANoOpWhenThereIsNoStagedEdit(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, REVERT_NOOP_TI_ID, "Kidney beans"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTriggerIngredient(tx, REVERT_NOOP_TI_ID, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_NOOP_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Kidney beans");
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
	}

	@Test
	@Order(20)
	void testRevertWorkingCopyOnlyTriggerIngredientIsRefused(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, "Mung beans"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTriggerIngredient(tx, newId, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Mung beans");
		assertThat(details.version()).isEqualTo(1L);
		assertThat(details.published()).isFalse();
	}

	@Test
	@Order(21)
	void testEditTriggerIngredientAcceptsAnExplanationAsLongAsTheMasterColumnHolds(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, "Chickpea flour"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, newId, "Chickpea flour", LONGEST_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.explanationForLlm()).isEqualTo(LONGEST_EXPLANATION);
	}

	@Test
	@Order(22)
	void testFindMasterRecommendationLinksIncludesTheRecommendationsSeededFromTheRules(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var links = factory.withoutTransaction(sut::findMasterRecommendationLinks).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(links.get(TRIGGER_INGREDIENT_ID)).containsExactly(DECREASE_RED_MEAT_RECOMMENDATION_ID);
	}

	@Test
	@Order(23)
	void testListForBackofficeOverlaysWorkingCopyOnMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, GRID_UNCHANGED_TI_ID, "Grid unchanged cheese"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> persistTriggerIngredient(tx, GRID_EDITED_TI_ID, "Grid edited cheese"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, GRID_EDITED_TI_ID, "Grid edited cheese (wc)", "Edited.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var wcOnlyId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, "Grid working-copy-only cheese"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var rows = factory.withoutTransaction(sut::listForBackoffice).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var byId = rows.stream().collect(Collectors.toMap(BackofficeTriggerIngredient::id, r -> r));

		assertThat(byId.get(GRID_UNCHANGED_TI_ID))
				.isEqualTo(new BackofficeTriggerIngredient(GRID_UNCHANGED_TI_ID, "Grid unchanged cheese", true, 0L));
		assertThat(byId.get(GRID_EDITED_TI_ID))
				.isEqualTo(new BackofficeTriggerIngredient(GRID_EDITED_TI_ID, "Grid edited cheese (wc)", true, 1L));
		assertThat(byId.get(wcOnlyId))
				.isEqualTo(new BackofficeTriggerIngredient(wcOnlyId, "Grid working-copy-only cheese", false, 1L));
		assertThat(rows).isSortedAccordingTo(Comparator.comparing(BackofficeTriggerIngredient::name));
	}

	@Test
	@Order(24)
	void testToggleRecommendationLinkStagesRemovalThenCollapsesBackToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRecommendation(tx, REC_C_ID, "Trigger grid component C")
						.chain(() -> persistTriggerIngredientLinkedTo(tx, TOGGLE_REMOVE_TI_ID, "Grid toggle-remove cheese", REC_C_ID)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.toggleRecommendationLink(tx, TOGGLE_REMOVE_TI_ID, REC_C_ID, false))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findStagedRecommendationLinks).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TOGGLE_REMOVE_TI_ID)).containsEntry(REC_C_ID, false);

		factory.withTransaction(tx -> sut.toggleRecommendationLink(tx, TOGGLE_REMOVE_TI_ID, REC_C_ID, true))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var collapsed = factory.withoutTransaction(sut::findStagedRecommendationLinks).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(collapsed).doesNotContainKey(TOGGLE_REMOVE_TI_ID);
	}

	@Test
	@Order(25)
	void testToggleRecommendationLinkStagesAdditionThenCollapsesBackToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRecommendation(tx, REC_D_ID, "Trigger grid component D")
						.chain(() -> persistTriggerIngredient(tx, TOGGLE_ADD_TI_ID, "Grid toggle-add cheese")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.toggleRecommendationLink(tx, TOGGLE_ADD_TI_ID, REC_D_ID, true))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findStagedRecommendationLinks).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TOGGLE_ADD_TI_ID)).containsEntry(REC_D_ID, true);

		factory.withTransaction(tx -> sut.toggleRecommendationLink(tx, TOGGLE_ADD_TI_ID, REC_D_ID, false))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var collapsed = factory.withoutTransaction(sut::findStagedRecommendationLinks).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(collapsed).doesNotContainKey(TOGGLE_ADD_TI_ID);
	}

	@Test
	@Order(26)
	void testFindAllReturnsThePublishedComponentsForScoringByMasterRecommendationInEveryLanguage(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		for (RecipeLanguage lang : RecipeLanguage.values()) {
			var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, lang))
					.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
			var beef = triggerIngredients.stream()
					.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
					.findFirst()
					.orElseThrow();
			assertThat(beef.getComponentsForScoring()).as("components in %s", lang)
					.containsExactly(new RecommendationImpl(DECREASE_RED_MEAT_RECOMMENDATION_NAME));
		}
		// an ingredient without published links has no components, and fetching the links does not repeat an ingredient
		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var unlinked = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(GRID_UNCHANGED_TI_ID))
				.findFirst()
				.orElseThrow();
		assertThat(unlinked.getComponentsForScoring()).isEmpty();
		assertThat(triggerIngredients).filteredOn(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID)).hasSize(1);
	}

	private static Uni<Void> persistTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, String name) {
		var entity = new TriggerIngredientEntity();
		entity.setId(id);
		entity.setName(name);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistTriggerIngredientTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var translation = new TriggerIngredientTranslationEntity();
		translation.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, id));
		translation.setLang(lang);
		translation.setName(name);
		translation.setExplanationForLlm(explanationForLlm);
		return tx.persist(translation).replaceWithVoid();
	}

	private static Uni<Void> persistRecommendation(ReactivePersistenceTxContext tx, UUID id, String componentForScoring) {
		var rec = new RecommendationEntity();
		rec.setId(id);
		rec.setName(componentForScoring);
		rec.setComponentForScoring(componentForScoring);
		rec.setTypeOfRecommendation(TypeOfRecommendation.LIMITED);
		return tx.persist(rec).replaceWithVoid();
	}

	private static Uni<Void> persistTriggerIngredientLinkedTo(ReactivePersistenceTxContext tx, UUID id, String name, UUID... recommendationIds) {
		var entity = new TriggerIngredientEntity();
		entity.setId(id);
		entity.setName(name);
		entity.setComponentsForScoring(Arrays.stream(recommendationIds)
				.map(recId -> tx.getReference(RecommendationEntity.class, recId))
				.collect(Collectors.toSet()));
		return tx.persist(entity).replaceWithVoid();
	}
}
