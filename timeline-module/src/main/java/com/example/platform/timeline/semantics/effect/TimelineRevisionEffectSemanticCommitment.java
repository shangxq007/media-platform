package com.example.platform.timeline.semantics.effect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: the Timeline revision semantic commitment
 * over Effect semantics — TIMELINE_REVISION_EFFECT_SEMANTIC_COMMITMENT_V1.
 *
 * <p>Corrected digest shape (bf7a2702):
 * <pre>
 * TimelineRevisionSemanticDigest =
 * H(TimelineCanonicalSemanticDigest, EffectSemanticContractVersion,
 *   EffectSemanticSnapshotContentDigest)
 * </pre>
 * EffectSemanticSnapshotId is EXCLUDED (EFFECT_SNAPSHOT_HANDLE_DOES_NOT_PARTICIPATE_IN_CANONICAL_SEMANTIC_DIGEST_V1):
 * same Timeline content + same Effect content digest + same contract version
 * ⇒ same revision semantic digest even when snapshot ids differ (RP3-B).
 * Different Effect content digest ⇒ different revision semantic digest (RP3-A).
 * Different contract version ⇒ different commitment by default (BI5).
 */
public final class TimelineRevisionEffectSemanticCommitment {

    private TimelineRevisionEffectSemanticCommitment() {
    }

    /**
     * Computes the revision-level semantic digest that includes Effect
     * semantics. Snapshot id intentionally absent.
     *
     * @param timelineCanonicalDigest the Timeline canonical semantic digest
     *                                (composition content)
     * @param reference               the pinned Effect snapshot reference
     * @return deterministic SHA-256 hex over (timeline, contractVersion,
     *         effectContentDigest)
     */
    public static String revisionEffectSemanticDigest(
            String timelineCanonicalDigest,
            EffectSemanticSnapshotReference reference) {
        Objects.requireNonNull(timelineCanonicalDigest, "timelineCanonicalDigest");
        Objects.requireNonNull(reference, "reference");
        String canonical = "timelineDigest=" + timelineCanonicalDigest + "\n"
                + reference.semanticCommitmentCanonical();
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
