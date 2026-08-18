package com.example.platform.timeline.semantics.relationship;

import com.example.platform.timeline.canonical.TimelineClipId;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): the
 * Relationship-local identity / normalization / canonical encoding authority.
 *
 * <p>Owns: typed relationship identity (Group = groupId; Sync = normalized
 * endpoint pair), deterministic canonical key, lossless canonical JSON encode.
 *
 * <p>Timeline keeps aggregate orchestration: relationship collection changes,
 * referenced clip existence, cross-object validation, three-way conflict
 * orchestration. Timeline must NOT re-derive relationship identity rules.
 *
 * <p>No generic graph model; unknown relationship variants fail closed rather
 * than degrading to unstable identity.
 */
public final class RelationshipCanonicalSemantics {

    private RelationshipCanonicalSemantics() {}

    /** Deterministic relationship identity key — stable across reloads.
     *  Unknown variants fail closed (never identityHashCode). */
    public static String canonicalKey(SemanticRelationship r) {
        if (r instanceof GroupRelationship g) {
            return "group:" + g.groupId().value();
        }
        if (r instanceof SyncRelationship s) {
            String a = s.endpointA().value();
            String b = s.endpointB().value();
            return a.compareTo(b) <= 0 ? "sync:" + a + ":" + b : "sync:" + b + ":" + a;
        }
        throw new IllegalStateException(
                "Unknown SemanticRelationship variant: " + r.getClass().getName());
    }

    /** Lossless canonical JSON encoding for op payloads. */
    public static String canonicalJson(SemanticRelationship r) {
        try {
            return com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .writeValueAsString(r);
        } catch (Exception e) {
            throw new IllegalStateException("SemanticRelationship canonical encoding failed", e);
        }
    }

    /** Lossless decode from canonical JSON (kind-driven; Jackson type metadata
     *  on SemanticRelationship is decode plumbing only). */
    public static SemanticRelationship fromCanonicalJson(String json) {
        try {
            JsonNode node = com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .readTree(json);
            return com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .treeToValue(node, SemanticRelationship.class);
        } catch (Exception e) {
            throw new IllegalStateException("SemanticRelationship canonical decode failed", e);
        }
    }

    /** Exact endpoints for Sync identity verification (Timeline-owned topology
     *  checks may use these without re-deriving ordering rules). */
    public static TimelineClipId[] syncEndpoints(SyncRelationship s) {
        return new TimelineClipId[] {s.endpointA(), s.endpointB()};
    }
}
