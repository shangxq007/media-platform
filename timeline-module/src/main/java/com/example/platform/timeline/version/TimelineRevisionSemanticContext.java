package com.example.platform.timeline.version;

import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.Objects;

/**
 * ROADMAP20 authority-integration correction + CLEAN-FORWARD addendum: the
 * REQUIRED revision-owned Effect semantic context of every valid canonical
 * TimelineRevision.
 *
 * <p>Clean-forward invariants (NO_UNSHIPPED_COMPATIBILITY_PATHS_V1):
 * <ul>
 *   <li>{@code semanticContext} is REQUIRED for every valid canonical revision
 *       — there is no legacy/no-context valid mode;</li>
 *   <li>the Effect semantic pin ({@code effectReference}) is REQUIRED — a valid
 *       revision always owns an authoritative EffectSemanticSnapshotReference
 *       (authoritative EMPTY for no-Effect revisions, never MISSING);</li>
 *   <li>{@code digestContractVersion = "revision-semantics-v1"} is the single
 *       valid revision semantic contract — the historical timeline-only digest
 *       mode does NOT exist as a runtime compatibility mode;</li>
 *   <li>missing context / missing pin in persisted data is INVALID/CORRUPT and
 *       FAILS CLOSED on read.</li>
 * </ul>
 */
public record TimelineRevisionSemanticContext(
        String timelineContentDigest,
        EffectSemanticSnapshotReference effectReference,
        String revisionSemanticDigest,
        String digestContractVersion) {

    /** The single valid revision semantic contract (no legacy timeline-only mode). */
    public static final String REVISION_SEMANTICS_V1 = "revision-semantics-v1";

    public TimelineRevisionSemanticContext {
        Objects.requireNonNull(timelineContentDigest, "timelineContentDigest");
        Objects.requireNonNull(effectReference, "effectReference");
        Objects.requireNonNull(revisionSemanticDigest, "revisionSemanticDigest");
        Objects.requireNonNull(digestContractVersion, "digestContractVersion");
        if (timelineContentDigest.isBlank() || revisionSemanticDigest.isBlank()) {
            throw new IllegalArgumentException("digests must not be blank");
        }
        if (!REVISION_SEMANTICS_V1.equals(digestContractVersion)) {
            throw new IllegalArgumentException(
                    "Unknown revision semantic contract '" + digestContractVersion
                            + "' — only '" + REVISION_SEMANTICS_V1 + "' is valid");
        }
    }
}
