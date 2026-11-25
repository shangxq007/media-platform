package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.MediaTimeJsonCodec;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical first-class Transition relationship (TIMELINE_EFFECT_TRANSITION
 * C9/C10): typed participants, exact MediaTime duration, alignment, temporal
 * policy, authored parameters. Timeline owns relationship topology; local
 * semantics (duration/alignment/parameters) belong to this record.
 */
public record CanonicalTransition(
        String transitionId,
        String transitionDefinitionId,
        String transitionDefinitionVersion,
        String outgoingClipId,
        String incomingClipId,
        String mediaType,
        @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
        @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
        MediaTime duration,
        String alignment,
        String temporalPolicy,
        Map<String, String> parameters) {

    public static final String CROSS_DISSOLVE = "video.cross-dissolve";
    public static final String CROSS_DISSOLVE_VERSION = "1.0";
    private static final Map<String, String> LINEAR_CURVE = Map.of("curve", "LINEAR");

    public static CanonicalTransition crossDissolve(
            String id, String outgoing, String incoming, MediaTime duration) {
        return new CanonicalTransition(id, CROSS_DISSOLVE, CROSS_DISSOLVE_VERSION,
                outgoing, incoming, "VIDEO", duration, "START_AT_INCOMING", "OVERLAP_TIMELINE", LINEAR_CURVE);
    }

    public boolean isCrossDissolve() {
        return CROSS_DISSOLVE.equals(transitionDefinitionId);
    }

    public boolean supportsCrossDissolve() {
        return isCrossDissolve() && CROSS_DISSOLVE_VERSION.equals(transitionDefinitionVersion)
                && "VIDEO".equals(mediaType) && "START_AT_INCOMING".equals(alignment)
                && "OVERLAP_TIMELINE".equals(temporalPolicy) && LINEAR_CURVE.equals(parameters);
    }

    public CanonicalTransition withDuration(MediaTime duration) {
        return new CanonicalTransition(transitionId, transitionDefinitionId, transitionDefinitionVersion,
                outgoingClipId, incomingClipId, mediaType, duration, alignment, temporalPolicy, parameters);
    }

    public CanonicalTransition {
        Objects.requireNonNull(transitionId, "transitionId");
        Objects.requireNonNull(transitionDefinitionId, "transitionDefinitionId");
        Objects.requireNonNull(outgoingClipId, "outgoingClipId");
        Objects.requireNonNull(incomingClipId, "incomingClipId");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(temporalPolicy, "temporalPolicy");
        if (transitionId.isBlank()) throw new IllegalArgumentException("transitionId must not be blank");
        if (outgoingClipId.equals(incomingClipId)) {
            throw new IllegalArgumentException("outgoingClipId must differ from incomingClipId");
        }
        if (duration.isLessThanOrEqualTo(MediaTime.ZERO)) {
            throw new IllegalArgumentException("duration must be > zero");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** Canonical serialization key used for deterministic ordering. */
    public String canonicalKey() {
        return transitionId;
    }
}
