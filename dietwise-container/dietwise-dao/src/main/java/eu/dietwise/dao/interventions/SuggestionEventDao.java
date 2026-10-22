package eu.dietwise.dao.interventions;

import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.services.model.interventions.SuggestionEvent;
import io.smallrye.mutiny.Uni;

public interface SuggestionEventDao {
	/** Append the Suggestion Event; events are never merged or overwritten. */
	Uni<Void> insert(ReactivePersistenceTxContext tx, SuggestionEvent event);

	/** Delete all Suggestion Events of the Intervention Assignments of the given user, if any. */
	Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId);
}
