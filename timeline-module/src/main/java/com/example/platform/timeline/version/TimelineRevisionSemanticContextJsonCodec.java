package com.example.platform.timeline.version;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/**
 * ROADMAP20 authority-integration correction: deterministic JSON
 * serialization of {@link TimelineRevisionSemanticContext} for durable
 * persistence (timeline_snapshot row {@code revctx_<revisionId>} — V1-only
 * Flyway governance preserved, no new migration).
 *
 * <p>On deserialize the revision semantic digest is RECOMPUTED from
 * (timelineContentDigest, contractVersion, effectContentDigest) and verified
 * against the persisted value (revision semantic digest verification on read,
 * §59 — closes DB pin tampering).
 */
public final class TimelineRevisionSemanticContextJsonCodec {

    private TimelineRevisionSemanticContextJsonCodec() {
    }

    public static String serialize(TimelineRevisionSemanticContext context) {
        ObjectNode root = InternalTimelineJson.mapper().createObjectNode();
        root.put("timelineContentDigest", context.timelineContentDigest());
        root.put("revisionSemanticDigest", context.revisionSemanticDigest());
        root.put("digestContractVersion", context.digestContractVersion());
        if (context.effectReference() != null) {
            ObjectNode pin = root.putObject("effectReference");
            pin.put("snapshotId", context.effectReference().snapshotId().value());
            pin.put("contentDigest", context.effectReference().contentDigest());
            pin.put("contractVersion", context.effectReference().semanticContractVersion().value());
        }
        try {
            return InternalTimelineJson.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize TimelineRevisionSemanticContext", e);
        }
    }

    public static TimelineRevisionSemanticContext deserialize(String payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            JsonNode root = InternalTimelineJson.mapper().readTree(payload);
            String timelineDigest = root.get("timelineContentDigest").asText();
            String revisionDigest = root.get("revisionSemanticDigest").asText();
            String contract = root.get("digestContractVersion").asText();
            EffectSemanticSnapshotReference effectRef = null;
            if (root.hasNonNull("effectReference")) {
                JsonNode pin = root.get("effectReference");
                effectRef = new EffectSemanticSnapshotReference(
                        com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId.of(
                                pin.get("snapshotId").asText()),
                        pin.get("contentDigest").asText(),
                        com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion.of(
                                pin.get("contractVersion").asText()));
            }
            TimelineRevisionSemanticContext context =
                    new TimelineRevisionSemanticContext(timelineDigest, effectRef, revisionDigest, contract);
            // verification on read (§59 + CLEAN-FORWARD): the revision semantic
            // digest is recomputed and must match; effectRef is REQUIRED
            // (enforced by the context constructor).
            if (effectRef == null) {
                throw new IllegalArgumentException(
                        "CORRUPT REVISION SEMANTIC CONTEXT: missing Effect pin — a valid revision "
                                + "must pin authoritative Effect semantics (never MISSING)");
            }
            String recomputed = com.example.platform.timeline.semantics.effect
                    .TimelineRevisionEffectSemanticCommitment
                    .revisionEffectSemanticDigest(timelineDigest, effectRef);
            if (!recomputed.equals(revisionDigest)) {
                throw new IllegalArgumentException(
                        "REVISION SEMANTIC DIGEST MISMATCH (DB tamper, §59): persisted '"
                                + revisionDigest + "' recomputed '" + recomputed + "'");
            }
            return context;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize TimelineRevisionSemanticContext (corrupt payload)", e);
        }
    }
}
