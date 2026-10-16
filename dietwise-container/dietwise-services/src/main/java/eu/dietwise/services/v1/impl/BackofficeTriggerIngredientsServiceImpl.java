package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.EntityInUseException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.suggestions.BackofficeTriggerIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.BackofficeTriggerIngredientsService;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedTriggerIngredient;
import eu.dietwise.services.v1.types.TriggerIngredientRecommendationGrid;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeTriggerIngredientsServiceImpl implements BackofficeTriggerIngredientsService {
	private final TriggerIngredientDao triggerIngredientDao;
	private final RecommendationDao recommendationDao;
	private final RuleDao ruleDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeTriggerIngredientsServiceImpl(
			TriggerIngredientDao triggerIngredientDao,
			RecommendationDao recommendationDao,
			RuleDao ruleDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.triggerIngredientDao = triggerIngredientDao;
		this.recommendationDao = recommendationDao;
		this.ruleDao = ruleDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<TriggerIngredientRecommendationGrid> recommendationGrid(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				recommendationDao.listForBackoffice(em),
				_ -> triggerIngredientDao.listForBackoffice(em),
				(_, _) -> triggerIngredientDao.findTranslationLangs(em),
				(_, _, _) -> triggerIngredientDao.findMasterRecommendationLinks(em),
				(_, _, _, _) -> triggerIngredientDao.findStagedRecommendationLinks(em),
				(_, _, _, _, _) -> ruleDao.findActiveRecommendationIdsByTriggerIngredient(em),
				this::toGrid
		));
	}

	@Override
	public Uni<Void> toggleRecommendation(User user, UUID triggerIngredientId, UUID recommendationId, boolean present) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> failIfRemovingTheRecommendationOfARule(tx, triggerIngredientId, recommendationId, present)
				.chain(() -> triggerIngredientDao.toggleRecommendationLink(tx, triggerIngredientId, recommendationId, present)));
	}

	private Uni<Void> failIfRemovingTheRecommendationOfARule(ReactivePersistenceTxContext tx, UUID triggerIngredientId, UUID recommendationId, boolean present) {
		if (present) {
			return Uni.createFrom().voidItem();
		} else {
			return ruleDao.findActiveRecommendationIdsByTriggerIngredient(tx).flatMap(byTriggerIngredient ->
					byTriggerIngredient.getOrDefault(triggerIngredientId, Set.of()).contains(recommendationId)
							? Uni.createFrom().failure(new EntityInUseException("Recommendation is assigned by an active Rule of the Trigger Ingredient and cannot be unlinked: " + triggerIngredientId + "/" + recommendationId))
							: Uni.createFrom().voidItem());
		}
	}

	private TriggerIngredientRecommendationGrid toGrid(
			List<BackofficeRecommendation> recommendations,
			List<BackofficeTriggerIngredient> triggerIngredients,
			Map<UUID, TranslationLangs> langsById,
			Map<UUID, Set<UUID>> masterLinks,
			Map<UUID, Map<UUID, Boolean>> stagedLinks,
			Map<UUID, Set<UUID>> ruleRecommendations
	) {
		List<RecommendationColumn> columns = RecommendationColumns.inDisplayOrder(recommendations);
		Set<UUID> columnIds = RecommendationColumns.ids(columns);
		List<StagedTriggerIngredient> rows = triggerIngredients.stream()
				.map(triggerIngredient -> new StagedTriggerIngredient(
						triggerIngredient.id(),
						triggerIngredient.name(),
						triggerIngredient.published(),
						triggerIngredient.version(),
						BackofficeTranslations.translationStates(triggerIngredient.id(), langsById),
						RecommendationColumns.retainColumns(masterLinks.get(triggerIngredient.id()), columnIds),
						RecommendationColumns.retainColumns(stagedLinks.containsKey(triggerIngredient.id()) ? stagedLinks.get(triggerIngredient.id()).keySet() : null, columnIds),
						RecommendationColumns.retainColumns(ruleRecommendations.get(triggerIngredient.id()), columnIds)))
				.toList();
		return new TriggerIngredientRecommendationGrid(columns, rows);
	}
}
