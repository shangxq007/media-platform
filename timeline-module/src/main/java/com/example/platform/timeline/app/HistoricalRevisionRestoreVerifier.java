package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.effect.EffectDefinitionCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.util.Objects;
import org.jooq.DSLContext;

/**
 * ROADMAP20 FINAL (R2): fail-closed verification boundary for restore.
 *
 * <p>A historical revision is restorable ONLY if its complete persisted
 * semantic closure independently revalidates
 * (RESTORE_HISTORICAL_REVISION_MUST_VERIFY_COMPLETE_SEMANTIC_COMMITMENT_V1,
 * RESTORE_REQUIRES_SINGLE_OWNERSHIP_CLOSURE_V1,
 * RESTORE_REQUIRES_SINGLE_SEMANTIC_COMMITMENT_CLOSURE_V1):
 *
 * <ol>
 *   <li>Timeline revision row (project P, tenant T — caller-validated);</li>
 *   <li>owned Timeline snapshot (P, T) — identity uniqueness is NOT authority;</li>
 *   <li>recomputed Timeline canonical digest == revctx.timelineContentDigest;</li>
 *   <li>owned Effect snapshot by the historical reference (P, T) — exact
 *       id/digest/contract match + internal definition digests;</li>
 *   <li>recomputed FULL revision semantic digest H(timeline, contract, effect)
 *       == revctx.revisionSemanticDigest == timeline_revision.content_hash
 *       (three-way equality).</li>
 * </ol>
 *
 * <p>This is ONLY a verification boundary — not a new authority, not a rule
 * engine. It never mints/remints Effect state; restore reissues the exact
 * historical commitment under a new revision identity.
 */
public final class HistoricalRevisionRestoreVerifier {

    private final TimelineSnapshotService timelineSnapshotService;
    private final EffectSemanticSnapshotStore effectSnapshotStore;
    private final TimelineContentDigester contentDigester;

    public HistoricalRevisionRestoreVerifier(
            TimelineSnapshotService timelineSnapshotService,
            EffectSemanticSnapshotStore effectSnapshotStore,
            TimelineContentDigester contentDigester) {
        this.timelineSnapshotService = Objects.requireNonNull(timelineSnapshotService, "timelineSnapshotService");
        this.effectSnapshotStore = Objects.requireNonNull(effectSnapshotStore, "effectSnapshotStore");
        this.contentDigester = Objects.requireNonNull(contentDigester, "contentDigester");
    }

    /** Verified historical semantic closure (owned + digest-agreeing). */
    public record VerifiedHistoricalRevision(
            String timelineDigest,
            String effectSnapshotId,
            String effectContentDigest,
            String fullRevisionSemanticDigest) {
    }

    /**
     * Verifies the complete historical semantic commitment and returns the
     * verified digest values. THROWS fail-closed on any mismatch, missing
     * object, ownership violation, or undecodable payload.
     */
    public VerifiedHistoricalRevision verify(DSLContext readDsl,
                                             String projectId, String tenantId,
                                             String historicalSnapshotId,
                                             String historicalContentHash,
                                             TimelineRevisionSemanticContext historicalContext) {
        // 1. ownership-scoped Timeline snapshot load (R1)
        TimelineSnapshotService.SnapshotInfo snapshot = timelineSnapshotService
                .findOwnedById(readDsl, projectId, tenantId, historicalSnapshotId)
                .orElseThrow(() -> new IllegalStateException(
                        "RESTORE FAIL CLOSED (R1/RST7): historical Timeline snapshot '"
                                + historicalSnapshotId + "' not owned by (" + projectId + ", "
                                + tenantId + ") — cross-ownership restore forbidden"));
        // 2. decode the persisted canonical Timeline document (no recovery
        // hydration; malformed payload FAILS CLOSED)
        TimelineDocument document;
        try {
            document = TimelineDocumentJsonSerializer.mapper()
                    .readerFor(TimelineDocument.class)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(snapshot.payloadJson());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST9): historical Timeline payload cannot be "
                            + "canonically decoded: " + e.getMessage(), e);
        }
        // 3. recomputed Timeline digest == revctx.timelineContentDigest
        String actualTimelineDigest = contentDigester.digest(document);
        if (!actualTimelineDigest.equals(historicalContext.timelineContentDigest())) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST9/HISTORICAL_TIMELINE_PAYLOAD_MUST_MATCH_PINNED_TIMELINE_DIGEST_V1): "
                            + "actual Timeline payload digest '" + actualTimelineDigest
                            + "' != revctx.timelineContentDigest '"
                            + historicalContext.timelineContentDigest() + "'");
        }
        // 4. ownership-scoped exact Effect snapshot resolution
        EffectSemanticSnapshotReference reference = historicalContext.effectReference();
        EffectSemanticSnapshot effectSnapshot = effectSnapshotStore
                .findById(projectId, tenantId, reference.snapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "RESTORE FAIL CLOSED (RST10/RST11): historical Effect snapshot '"
                                + reference.snapshotId() + "' missing or not owned by ("
                                + projectId + ", " + tenantId + ")"));
        if (!effectSnapshot.id().equals(reference.snapshotId())
                || !effectSnapshot.contentDigest().equals(reference.contentDigest())
                || !effectSnapshot.semanticContractVersion().equals(reference.semanticContractVersion())) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST12): historical Effect snapshot does not match "
                            + "the pinned exact reference (id/digest/contract)");
        }
        // internal Effect snapshot integrity (definition digests, snapshot digest)
        String recomputedEffectDigest =
                com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotCanonicalSemantics
                        .snapshotContentDigest(effectSnapshot);
        if (!recomputedEffectDigest.equals(effectSnapshot.contentDigest())) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED: historical Effect snapshot digest does not recompute");
        }
        for (var entry : effectSnapshot.entries()) {
            EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(entry.definitionSnapshot());
        }
        // 5. recomputed FULL revision semantic digest == revctx == content hash
        String recomputedFull = com.example.platform.timeline.semantics.effect
                .TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(
                        actualTimelineDigest, reference);
        if (!recomputedFull.equals(historicalContext.revisionSemanticDigest())) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST8): recomputed full semantic digest '"
                            + recomputedFull + "' != revctx.revisionSemanticDigest '"
                            + historicalContext.revisionSemanticDigest() + "'");
        }
        if (historicalContentHash != null
                && !recomputedFull.equals(historicalContentHash)) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST8): recomputed full semantic digest '"
                            + recomputedFull + "' != timeline_revision.content_hash '"
                            + historicalContentHash + "' — 3-way digest equality violated");
        }
        return new VerifiedHistoricalRevision(
                actualTimelineDigest, reference.snapshotId().value(),
                reference.contentDigest(), recomputedFull);
    }
}
