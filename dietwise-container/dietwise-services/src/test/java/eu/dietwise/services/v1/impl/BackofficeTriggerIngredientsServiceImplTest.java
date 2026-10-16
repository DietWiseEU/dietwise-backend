package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.EntityInUseException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.suggestions.BackofficeTriggerIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedTriggerIngredient;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.TypeOfRecommendation;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeTriggerIngredientsServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);

	private static final UUID REC_VEGETABLES_ID = UUID.fromString("b1b2b3b4-0011-4f5a-8b9c-0d1e2f3a0011");
	private static final UUID REC_RED_MEAT_ID = UUID.fromString("b1b2b3b4-0012-4f5a-8b9c-0d1e2f3a0012");
	private static final UUID REC_SODIUM_ID = UUID.fromString("b1b2b3b4-0013-4f5a-8b9c-0d1e2f3a0013");
	private static final UUID REC_NOT_A_COLUMN_ID = UUID.fromString("b1b2b3b4-0014-4f5a-8b9c-0d1e2f3a0014");
	private static final UUID TI_BEEF_ID = UUID.fromString("d1d2d3d4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID TI_FETA_ID = UUID.fromString("d1d2d3d4-0002-4f5a-8b9c-0d1e2f3a0002");

	@Mock
	private TriggerIngredientDao triggerIngredientDao;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private RuleDao ruleDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void recommendationGridOverlaysLinksAndMarksTheRecommendationsOfTheRulesForAnAdmin() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(REC_RED_MEAT_ID, "Decrease red meat", "red meat", TypeOfRecommendation.LIMITED, null, null),
				new BackofficeRecommendation(REC_VEGETABLES_ID, "Increase vegetables", "vegetables", TypeOfRecommendation.ENCOURAGED, null, null),
				new BackofficeRecommendation(REC_SODIUM_ID, "Decrease sodium", "sodium", TypeOfRecommendation.LIMITED, null, null))));
		when(triggerIngredientDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeTriggerIngredient(TI_BEEF_ID, "Beef", true, 0L),
				new BackofficeTriggerIngredient(TI_FETA_ID, "Feta", false, 1L))));
		when(triggerIngredientDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_BEEF_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class)))));
		when(triggerIngredientDao.findMasterRecommendationLinks(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_BEEF_ID, Set.of(REC_RED_MEAT_ID, REC_NOT_A_COLUMN_ID))));
		when(triggerIngredientDao.findStagedRecommendationLinks(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_FETA_ID, Map.of(REC_SODIUM_ID, true))));
		when(ruleDao.findActiveRecommendationIdsByTriggerIngredient(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_BEEF_ID, Set.of(REC_RED_MEAT_ID, REC_NOT_A_COLUMN_ID))));

		TriggerIngredientRecommendationGrid grid = newService().recommendationGrid(adminUser()).await().atMost(AWAIT);

		assertThat(grid.columns()).containsExactly(
				new RecommendationColumn(REC_VEGETABLES_ID, "vegetables", TypeOfRecommendation.ENCOURAGED),
				new RecommendationColumn(REC_RED_MEAT_ID, "red meat", TypeOfRecommendation.LIMITED),
				new RecommendationColumn(REC_SODIUM_ID, "sodium", TypeOfRecommendation.LIMITED));
		assertThat(grid.triggerIngredients()).hasSize(2);

		StagedTriggerIngredient beef = grid.triggerIngredients().get(0);
		assertThat(beef.id()).isEqualTo(TI_BEEF_ID);
		assertThat(beef.name()).isEqualTo("Beef");
		assertThat(beef.published()).isTrue();
		assertThat(beef.version()).isZero();
		assertThat(beef.linkedRecommendationIds()).containsExactly(REC_RED_MEAT_ID);
		assertThat(beef.stagedRecommendationIds()).isEmpty();
		assertThat(beef.ruleRecommendationIds()).containsExactly(REC_RED_MEAT_ID);
		assertThat(beef.translations())
				.containsEntry(RecipeLanguage.EL, TranslationState.PRESENT)
				.containsEntry(RecipeLanguage.NL, TranslationState.MISSING);

		StagedTriggerIngredient feta = grid.triggerIngredients().get(1);
		assertThat(feta.id()).isEqualTo(TI_FETA_ID);
		assertThat(feta.published()).isFalse();
		assertThat(feta.version()).isEqualTo(1L);
		assertThat(feta.linkedRecommendationIds()).isEmpty();
		assertThat(feta.stagedRecommendationIds()).containsExactly(REC_SODIUM_ID);
		assertThat(feta.ruleRecommendationIds()).isEmpty();
		assertThat(feta.translations().values()).containsOnly(TranslationState.MISSING);

		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void recommendationGridRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().recommendationGrid(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).listForBackoffice(any());
		verify(triggerIngredientDao, never()).listForBackoffice(any());
		verify(ruleDao, never()).findActiveRecommendationIdsByTriggerIngredient(any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void toggleRecommendationStagesAnAdditionInATransactionForAnAdmin() {
		when(triggerIngredientDao.toggleRecommendationLink(any(), eq(TI_BEEF_ID), eq(REC_SODIUM_ID), eq(true)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().toggleRecommendation(adminUser(), TI_BEEF_ID, REC_SODIUM_ID, true).await().atMost(AWAIT);

		verify(triggerIngredientDao).toggleRecommendationLink(any(), eq(TI_BEEF_ID), eq(REC_SODIUM_ID), eq(true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void toggleRecommendationStagesTheRemovalOfARecommendationNoRuleAssigns() {
		when(ruleDao.findActiveRecommendationIdsByTriggerIngredient(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_BEEF_ID, Set.of(REC_RED_MEAT_ID))));
		when(triggerIngredientDao.toggleRecommendationLink(any(), eq(TI_BEEF_ID), eq(REC_SODIUM_ID), eq(false)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().toggleRecommendation(adminUser(), TI_BEEF_ID, REC_SODIUM_ID, false).await().atMost(AWAIT);

		verify(triggerIngredientDao).toggleRecommendationLink(any(), eq(TI_BEEF_ID), eq(REC_SODIUM_ID), eq(false));
	}

	@Test
	void toggleRecommendationRefusesToRemoveTheRecommendationOfAnActiveRule() {
		when(ruleDao.findActiveRecommendationIdsByTriggerIngredient(any())).thenReturn(Uni.createFrom().item(Map.of(
				TI_BEEF_ID, Set.of(REC_RED_MEAT_ID))));

		assertThatThrownBy(() -> newService().toggleRecommendation(adminUser(), TI_BEEF_ID, REC_RED_MEAT_ID, false).await().atMost(AWAIT))
				.isInstanceOf(EntityInUseException.class);
		verify(triggerIngredientDao, never()).toggleRecommendationLink(any(), any(), any(), anyBoolean());
	}

	@Test
	void toggleRecommendationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().toggleRecommendation(nonAdminUser(), TI_BEEF_ID, REC_SODIUM_ID, true).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).toggleRecommendationLink(any(), any(), any(), anyBoolean());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeTriggerIngredientsServiceImpl newService() {
		return new BackofficeTriggerIngredientsServiceImpl(
				triggerIngredientDao, recommendationDao, ruleDao, persistenceContextFactory, new AuthorizationImpl());
	}

	private static User adminUser() {
		return user(EnumSet.of(Role.ADMIN));
	}

	private static User nonAdminUser() {
		return user(EnumSet.of(Role.CITIZEN));
	}

	private static User user(EnumSet<Role> roles) {
		return ImmutableUser.builder()
				.id(new UserIdImpl("00000000-0000-0000-0000-000000000009"))
				.name("editor@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(roles)
				.build();
	}
}
