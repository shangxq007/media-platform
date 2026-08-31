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
 *   <li>recomputed Timeline digest == timeline_revision.content_hash;</li>
 *   <li>recomputed FULL revision semantic digest H(timeline, contract, effect)
 *       == revctx.revisionSemanticDigest.</li>
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

    /** Verified historical semantic closure (owned + digest-agreeing).
     *
     * <p>FINAL (C1): carries the VERIFIED Timeline state itself — the decoded
     * document, schema version, verified Timeline digest, verified Effect
     * reference and the verified FULL revision semantic digest. Restore MUST
     * reissue directly from this value
     * (RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1); it must NOT
     * reread the historical snapshot or historical context after
     * verification (VERIFIED_RESTORE_STATE_IS_ATOMIC_AS_A_SEMANTIC_VALUE_V1).
     */
    public record VerifiedHistoricalRevision(
            TimelineDocument document,
            String timelineSchemaVersion,
            String timelineDigest,
            EffectSemanticSnapshotReference effectReference,
            String fullRevisionSemanticDigest,
            String digestContractVersion) {
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
            document = TimelineDocumentJsonSerializer.deserialize(snapshot.payloadJson());
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
        // 5. recomputed FULL revision semantic digest == the separate context authority
        String recomputedFull = com.example.platform.timeline.semantics.effect
                .TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(
                        actualTimelineDigest, reference);
        if (!recomputedFull.equals(historicalContext.revisionSemanticDigest())) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST8): recomputed full semantic digest '"
                            + recomputedFull + "' != revctx.revisionSemanticDigest '"
                            + historicalContext.revisionSemanticDigest() + "'");
        }
        // H7 V2 digest convergence: content_hash is only the canonical Timeline
        // content digest. The full Timeline+Effect commitment is separate above.
        Objects.requireNonNull(historicalContentHash,
                "RESTORE FAIL CLOSED: historical timeline_revision.content_hash "
                        + "is null — missing persisted Timeline commitment is INVALID/CORRUPT");
        if (!actualTimelineDigest.equals(historicalContentHash)) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST8): recomputed Timeline content digest '"
                            + actualTimelineDigest + "' != timeline_revision.content_hash '"
                            + historicalContentHash + "'");
        }
        // C1: the verified result carries the EXACT verified Timeline state —
        // decoded document + canonical payload bytes + schema version + digest
        // + Effect reference + full commitment — so restore reissues directly
        // from this value without any post-verification reread.
        return new VerifiedHistoricalRevision(
                document,
                snapshot.schemaVersion(),
                actualTimelineDigest,
                reference,
                recomputedFull,
                historicalContext.digestContractVersion());
    }
}
