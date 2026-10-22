package eu.dietwise.dao.interventions;

import java.util.Optional;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import io.smallrye.mutiny.Uni;

public interface InterventionAssignmentDao {
	/** Insert the Intervention Assignment together with its Suggestions. */
	Uni<Void> insert(ReactivePersistenceTxContext tx, InterventionAssignment assignment);

	/** Find the Intervention Assignment of the given assessment, with its Suggestions. */
	Uni<Optional<InterventionAssignment>> findById(ReactivePersistenceContext em, UUID assessmentId);

	/** Delete all Intervention Assignments of the given user, with their Suggestions, if any. */
	Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId);
}
