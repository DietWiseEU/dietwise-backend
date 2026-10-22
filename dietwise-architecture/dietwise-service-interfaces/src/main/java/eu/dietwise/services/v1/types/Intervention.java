package eu.dietwise.services.v1.types;

import eu.dietwise.common.types.Nullable;

/**
 * An Intervention as shown to the user, with its text in the user's language; the "no Intervention" arm has no text.
 */
public record Intervention(int id, @Nullable String text) {
}
