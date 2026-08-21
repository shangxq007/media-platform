package com.example.platform.timeline.version;

import com.example.platform.timeline.canonical.TimelineDocument;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable TimelineRevision - represents a single immutable timeline snapshot.
 *
 * <p>ROADMAP20 authority-integration correction + CLEAN-FORWARD addendum: every
 * VALID canonical revision owns its {@link TimelineRevisionSemanticContext}
 * (exact Effect semantic pin + Timeline digest + full revision semantic
 * digest). There is NO compatibility constructor and no valid revision without
 * semantic context (NO_UNSHIPPED_COMPATIBILITY_PATHS_V1).
 */
public record TimelineRevision(
        String revisionId,
        String productId,
        String parentRevisionId,
        String timelineSchemaVersion,
        TimelineDocument canonicalTimeline,
        String contentDigest,
        Instant createdAt,
        String createdBy,
        TimelineRevisionSemanticContext semanticContext) {

    public TimelineRevision {
        if (revisionId == null || revisionId.isBlank())
            throw new IllegalArgumentException("revisionId must not be blank");
        if (productId == null || productId.isBlank())
            throw new IllegalArgumentException("productId must not be blank");
        if (timelineSchemaVersion == null || timelineSchemaVersion.isBlank())
            throw new IllegalArgumentException("timelineSchemaVersion must not be blank");
        if (contentDigest == null || contentDigest.isBlank())
            throw new IllegalArgumentException("contentDigest must not be blank");
        if (createdAt == null)
            throw new IllegalArgumentException("createdAt must not be null");
        // CLEAN-FORWARD: semanticContext is REQUIRED for every valid canonical
        // revision (no legacy MISSING mode). canonicalTimeline MAY be null when
        // loading from DB without the full document.
        if (semanticContext == null) {
            throw new IllegalArgumentException(
                    "VALID_CANONICAL_REVISION_REQUIRES_SEMANTIC_CONTEXT_V1: revision "
                            + revisionId + " must own its TimelineRevisionSemanticContext "
                            + "(authoritative Effect pin + revision semantic digest)");
        }
        if (semanticContext.effectReference() == null) {
            throw new IllegalArgumentException(
                    "VALID_CANONICAL_REVISION_REQUIRES_EFFECT_SEMANTIC_SNAPSHOT_REFERENCE_V1: revision "
                            + revisionId + " must own an exact Effect semantic pin");
        }
        if (!contentDigest.equals(semanticContext.revisionSemanticDigest())) {
            throw new IllegalArgumentException(
                    "REVISION DIGEST MISMATCH: contentDigest '" + contentDigest
                            + "' must equal the semantic context revision semantic digest '"
                            + semanticContext.revisionSemanticDigest() + "'");
        }
    }

    public boolean isRoot() {
        return parentRevisionId == null;
    }

    /** The revision-owned exact Effect semantic pin (always present for valid revisions). */
    public com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference effectSemanticSnapshotReference() {
        return semanticContext().effectReference();
    }

    /**
     * ROADMAP20 authority-integration (§15): hydrates this revision with its
     * OWN persisted canonical document. The supplied document MUST match the
     * semantic context's committed Timeline content digest — otherwise FAIL
     * CLOSED. No caller can change canonical Timeline content through this
     * method: any content mutation must go through the canonical revision
     * writer (which mints a NEW revision semantic digest).
     */
    public TimelineRevision hydrate(TimelineDocument document) {
        Objects.requireNonNull(document, "document");
        String computed = new com.example.platform.timeline.canonical.TimelineContentDigester()
                .digest(document);
        if (!computed.equals(semanticContext.timelineContentDigest())) {
            throw new IllegalArgumentException(
                    "TIMELINE_REVISION_HYDRATION_DIGEST_MISMATCH_V1: supplied document digest '"
                            + computed + "' does not match the revision semantic context's committed "
                            + "Timeline content digest '" + semanticContext.timelineContentDigest()
                            + "' — hydration must use the revision's OWN persisted document; semantic "
                            + "content mutation is only valid through the canonical revision writer");
        }
        return new TimelineRevision(revisionId, productId, parentRevisionId,
                timelineSchemaVersion, document, contentDigest, createdAt, createdBy, semanticContext);
    }
}
