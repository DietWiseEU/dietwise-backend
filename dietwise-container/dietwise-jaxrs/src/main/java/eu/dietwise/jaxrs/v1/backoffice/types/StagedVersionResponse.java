package eu.dietwise.jaxrs.v1.backoffice.types;

/**
 * The Working Copy version of a Rule after a Staged Change, returned so the client can base its next edit on it.
 */
public record StagedVersionResponse(long version) {
}
