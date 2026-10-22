package eu.dietwise.dao.interventions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.interventions.TranslatedIntervention;
import io.smallrye.mutiny.Uni;

public interface InterventionDao {
	/** Find the active Interventions, ordered by id. */
	Uni<List<TranslatedIntervention>> findActive(ReactivePersistenceContext em);
}
