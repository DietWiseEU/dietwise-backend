package eu.dietwise.dao.impl.interventions;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Subquery;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.interventions.SuggestionEventDao;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentEntity;
import eu.dietwise.dao.jpa.interventions.InterventionAssignmentEntity_;
import eu.dietwise.dao.jpa.interventions.SuggestionEventEntity;
import eu.dietwise.dao.jpa.interventions.SuggestionEventEntity_;
import eu.dietwise.services.model.interventions.SuggestionEvent;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionEventDaoImpl implements SuggestionEventDao {
	@Override
	public Uni<Void> insert(ReactivePersistenceTxContext tx, SuggestionEvent event) {
		var entity = new SuggestionEventEntity();
		entity.setId(UUID.randomUUID());
		entity.setInterventionAssignmentAssessmentId(event.getAssessmentId());
		entity.setSuggestionTemplateId(event.getSuggestionTemplateId().asUuid());
		entity.setAction(event.getAction());
		entity.setCreatedAt(event.getCreatedAt());
		return tx.persistAll(entity);
	}

	@Override
	public Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<SuggestionEventEntity> delete = cb.createCriteriaDelete(SuggestionEventEntity.class);
		var root = delete.from(SuggestionEventEntity.class);
		Subquery<UUID> assessmentIdsOfUser = delete.subquery(UUID.class);
		var assignmentsRoot = assessmentIdsOfUser.from(InterventionAssignmentEntity.class);
		assessmentIdsOfUser.select(assignmentsRoot.get(InterventionAssignmentEntity_.assessmentId))
				.where(cb.equal(assignmentsRoot.get(InterventionAssignmentEntity_.userId), userId));
		delete.where(root.get(SuggestionEventEntity_.interventionAssignmentAssessmentId).in(assessmentIdsOfUser));
		return tx.createDelete(delete).execute().replaceWithVoid();
	}
}
