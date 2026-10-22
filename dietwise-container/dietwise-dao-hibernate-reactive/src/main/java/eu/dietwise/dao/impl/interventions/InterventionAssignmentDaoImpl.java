package eu.dietwise.dao.impl.interventions;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Subquery;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.interventions.InterventionAssignmentDao;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentEntity;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentEntity_;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentSuggestionEntity;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentSuggestionEntity_;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class InterventionAssignmentDaoImpl implements InterventionAssignmentDao {
	@Override
	public Uni<Void> insert(ReactivePersistenceTxContext tx, InterventionAssignment assignment) {
		var entity = new InterventionAssignmentEntity();
		entity.setAssessmentId(assignment.getAssessmentId());
		entity.setUserId(assignment.getUserId());
		entity.setApplicationId(assignment.getApplicationId());
		entity.setInterventionId(assignment.getInterventionId());
		entity.setUiLang(assignment.getUiLang());
		entity.setRecipeUrl(assignment.getRecipeUrl());
		entity.setCreatedAt(assignment.getCreatedAt());
		Stream<InterventionAssignmentSuggestionEntity> suggestions = assignment.getSuggestionTemplateIds().stream().map(suggestionTemplateId -> {
			var suggestion = new InterventionAssignmentSuggestionEntity();
			suggestion.setInterventionAssignmentAssessmentId(assignment.getAssessmentId());
			suggestion.setSuggestionTemplateId(suggestionTemplateId.asUuid());
			return suggestion;
		});
		return tx.persistAll(Stream.concat(Stream.of(entity), suggestions).toArray());
	}

	@Override
	public Uni<Optional<InterventionAssignment>> findById(ReactivePersistenceContext em, UUID assessmentId) {
		return forcm(
				em.find(InterventionAssignmentEntity.class, assessmentId),
				entity -> entity == null ? Uni.createFrom().item(List.<InterventionAssignmentSuggestionEntity>of()) : findSuggestions(em, assessmentId),
				(entity, suggestions) -> Optional.ofNullable(entity).map(e -> toInterventionAssignment(e, suggestions))
		);
	}

	@Override
	public Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<InterventionAssignmentSuggestionEntity> deleteSuggestions = cb.createCriteriaDelete(InterventionAssignmentSuggestionEntity.class);
		var suggestionsRoot = deleteSuggestions.from(InterventionAssignmentSuggestionEntity.class);
		Subquery<UUID> assessmentIdsOfUser = deleteSuggestions.subquery(UUID.class);
		var assignmentsRoot = assessmentIdsOfUser.from(InterventionAssignmentEntity.class);
		assessmentIdsOfUser.select(assignmentsRoot.get(InterventionAssignmentEntity_.assessmentId))
				.where(cb.equal(assignmentsRoot.get(InterventionAssignmentEntity_.userId), userId));
		deleteSuggestions.where(suggestionsRoot.get(InterventionAssignmentSuggestionEntity_.interventionAssignmentAssessmentId).in(assessmentIdsOfUser));

		CriteriaDelete<InterventionAssignmentEntity> deleteAssignments = cb.createCriteriaDelete(InterventionAssignmentEntity.class);
		var root = deleteAssignments.from(InterventionAssignmentEntity.class);
		deleteAssignments.where(cb.equal(root.get(InterventionAssignmentEntity_.userId), userId));

		return tx.createDelete(deleteSuggestions).execute()
				.chain(() -> tx.createDelete(deleteAssignments).execute())
				.replaceWithVoid();
	}

	private Uni<List<InterventionAssignmentSuggestionEntity>> findSuggestions(ReactivePersistenceContext em, UUID assessmentId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(InterventionAssignmentSuggestionEntity.class);
		var root = q.from(InterventionAssignmentSuggestionEntity.class);
		q.where(cb.equal(root.get(InterventionAssignmentSuggestionEntity_.interventionAssignmentAssessmentId), assessmentId));
		return em.createQuery(q).getResultList();
	}

	private InterventionAssignment toInterventionAssignment(InterventionAssignmentEntity entity, List<InterventionAssignmentSuggestionEntity> suggestions) {
		return ImmutableInterventionAssignment.builder()
				.assessmentId(entity.getAssessmentId())
				.userId(entity.getUserId())
				.applicationId(entity.getApplicationId())
				.interventionId(entity.getInterventionId())
				.uiLang(entity.getUiLang())
				.recipeUrl(entity.getRecipeUrl())
				.createdAt(entity.getCreatedAt())
				.suggestionTemplateIds(suggestions.stream().map(s -> new GenericSuggestionTemplateId(s.getSuggestionTemplateId().toString())).toList())
				.build();
	}
}
