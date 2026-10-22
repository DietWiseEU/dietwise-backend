package eu.dietwise.services.v1.interventions;

import java.util.UUID;

import eu.dietwise.services.v1.types.Intervention;

/**
 * The Intervention assigned to an assessment, as it will be sent to the client.
 */
public record AssignedIntervention(UUID assessmentId, Intervention intervention) {
}
