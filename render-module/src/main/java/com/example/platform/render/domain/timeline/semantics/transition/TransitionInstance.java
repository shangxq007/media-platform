package com.example.platform.render.domain.timeline.semantics.transition;

import com.example.platform.shared.time.MediaTime;

import java.util.Objects;

/**
 * Transition model: a stable, strong-typed, individually addressable entity
 * connecting two clips at a cut point.
 *
 * @param transitionId          stable, unique identifier
 * @param transitionDefinitionId definition reference
 * @param transitionDefinitionVersion version of the definition
 * @param outgoingClipId        the clip exiting (left side)
 * @param incomingClipId        the clip entering (right side)
 * @param mediaType             video, audio, or audio+video
 * @param duration              overlap duration of the transition
 * @param alignment             how the transition aligns with the cut
 * @param temporalPolicy        how timeline duration is affected
 * @param parameters            typed parameter map
 */
public record TransitionInstance(
    String transitionId,
    String transitionDefinitionId,
    String transitionDefinitionVersion,
    String outgoingClipId,
    String incomingClipId,
    TransitionMediaType mediaType,
    MediaTime duration,
    TransitionAlignment alignment,
    TransitionTemporalPolicy temporalPolicy,
    java.util.Map<String, String> parameters
) {
    public TransitionInstance {
        Objects.requireNonNull(transitionId, "transitionId");
        Objects.requireNonNull(outgoingClipId, "outgoingClipId");
        Objects.requireNonNull(incomingClipId, "incomingClipId");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(temporalPolicy, "temporalPolicy");
        if (transitionId.isBlank()) throw new IllegalArgumentException("transitionId must not be blank");
        if (outgoingClipId.equals(incomingClipId)) throw new IllegalArgumentException("outgoingClipId must differ from incomingClipId");
        if (duration.isEqualTo(MediaTime.ZERO) || duration.isLessThan(MediaTime.ZERO)) {
            throw new IllegalArgumentException("duration must be > zero");
        }
        parameters = parameters != null ? java.util.Map.copyOf(parameters) : java.util.Map.of();
    }

    /**
     * Media type enumeration for transitions.
     */
    public enum TransitionMediaType {
        VIDEO, AUDIO, AUDIO_VIDEO
    }

    /**
     * Alignment of transition relative to the cut point.
     */
    public enum TransitionAlignment {
        CENTER_ON_CUT,
        START_AT_CUT,
        END_AT_CUT,
        CUSTOM_OFFSET
    }

    /**
     * Temporal policy: how the transition affects timeline duration.
     * <p>
     * - USE_SOURCE_HANDLES: transition takes duration from clip handles (no timeline change).
     * - OVERLAP_TIMELINE: outgoing and incoming overlap; timeline duration reduced.
     * - INSERT_DURATION: explicitly declared duration is added to timeline.
     */
    public enum TransitionTemporalPolicy {
        USE_SOURCE_HANDLES,
        OVERLAP_TIMELINE,
        INSERT_DURATION
    }

    /**
     * Returns the cut point (in timeline time) where this transition is anchored.
     */
    public String getCutAnchor() {
        return outgoingClipId + "->" + incomingClipId;
    }
}
