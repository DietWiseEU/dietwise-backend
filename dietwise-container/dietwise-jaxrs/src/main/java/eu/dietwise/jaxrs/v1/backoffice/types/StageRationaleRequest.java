package eu.dietwise.jaxrs.v1.backoffice.types;

/**
 * Request to stage a Rule's rationale in the Working Copy.
 *
 * @param rationale   The proposed rationale; may be {@code null}
 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record StageRationaleRequest(String rationale, long baseVersion) {
}
