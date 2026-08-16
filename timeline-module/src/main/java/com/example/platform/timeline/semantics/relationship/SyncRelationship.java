package com.example.platform.timeline.semantics.relationship;

import com.example.platform.timeline.canonical.MediaTimeJsonCodec;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SR10/SR11/IR3): exact
 * object-local anchor correspondence between two canonical Timeline clips.
 *
 * <p>V1 means EXACTLY: object-local anchor on endpoint A corresponds to
 * object-local anchor on endpoint B. It does NOT mean continuous sync/rate
 * lock/phase lock/same duration/linked editing/auto mutation. Anchors are
 * exact MediaTime in OBJECT-LOCAL time; never source time.
 *
 * <p>Semantic identity (IR2): kind + normalized endpoint pair. Anchors are
 * mutable semantic CONTENT, not identity. Sync(A,a,B,b) == Sync(B,b,A,a)
 * (symmetric normalization moves endpoint-anchor pairs together).
 */
public record SyncRelationship(TimelineClipId endpointA,
                               @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
                               @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
                               MediaTime localAnchorA,
                               TimelineClipId endpointB,
                               @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
                               @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
                               MediaTime localAnchorB)
        implements SemanticRelationship {

    public SyncRelationship {
        Objects.requireNonNull(endpointA, "endpointA");
        Objects.requireNonNull(localAnchorA, "localAnchorA");
        Objects.requireNonNull(endpointB, "endpointB");
        Objects.requireNonNull(localAnchorB, "localAnchorB");
        if (endpointA.equals(endpointB)) {
            throw new IllegalArgumentException("sync self-edge rejected");
        }
    }

    /** Symmetric normalization: endpoints ordered, anchors move WITH endpoints. */
    public static SyncRelationship of(TimelineClipId a, MediaTime anchorA,
                                      TimelineClipId b, MediaTime anchorB) {
        if (a.equals(b)) {
            throw new IllegalArgumentException("sync self-edge rejected");
        }
        if (a.compareTo(b) <= 0) {
            return new SyncRelationship(a, anchorA, b, anchorB);
        }
        return new SyncRelationship(b, anchorB, a, anchorA);
    }

    /** Semantic identity key: kind + normalized endpoint pair (anchors excluded). */
    public String identityKey() {
        return "SYNC(" + endpointA.value() + "," + endpointB.value() + ")";
    }

    @Override
    public Kind kind() {
        return Kind.SYNC;
    }
}
