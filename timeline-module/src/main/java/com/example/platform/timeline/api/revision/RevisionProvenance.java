package com.example.platform.timeline.api.revision;

/**
 * Additive commit provenance for one canonical revision write.
 *
 * <p>It is written inside the same physical transaction as the revision row,
 * snapshot, parent edge and ref CAS. The Timeline owner treats
 * {@code changeSummaryJson} as an opaque, format-versioned document; it never
 * interprets operation semantics. {@code message} is the optional commit message.
 */
public record RevisionProvenance(
        String formatVersion,
        String message,
        String changeSummaryJson) {

    public static final int MAX_MESSAGE_LENGTH = 512;
    public static final int MAX_SUMMARY_LENGTH = 262_144;

    public RevisionProvenance {
        if (formatVersion == null || formatVersion.isBlank() || formatVersion.length() > 64) {
            throw new IllegalArgumentException("formatVersion required");
        }
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("commit message exceeds " + MAX_MESSAGE_LENGTH + " characters");
        }
        if (changeSummaryJson == null || changeSummaryJson.isBlank()) {
            throw new IllegalArgumentException("changeSummaryJson required");
        }
        if (changeSummaryJson.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("change summary exceeds " + MAX_SUMMARY_LENGTH + " characters");
        }
    }
}
