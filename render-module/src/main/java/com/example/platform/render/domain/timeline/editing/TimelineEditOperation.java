package com.example.platform.render.domain.timeline.editing;

import java.util.Map;
import java.util.Objects;

/**
 * A single semantic edit operation on a timeline.
 * Immutable record. Internal domain model.
 *
 * <p>Operations are semantic, side-effect-free, and do not persist or render.</p>
 *
 * @param type        operation type
 * @param targetId    target entity id (track id, clip id, etc.) — nullable for CREATE_TIMELINE
 * @param parameters  operation-specific parameters
 * @param safeMetadata safe metadata only
 */
public record TimelineEditOperation(
        TimelineEditOperationType type,
        String targetId,
        Map<String, Object> parameters,
        Map<String, String> safeMetadata) {

    public TimelineEditOperation {
        Objects.requireNonNull(type, "type must not be null");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * Creates a CREATE_TIMELINE operation.
     */
    public static TimelineEditOperation createTimeline(Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.CREATE_TIMELINE, null, params, Map.of());
    }

    /**
     * Creates an ADD_TRACK operation.
     */
    public static TimelineEditOperation addTrack(String trackId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_TRACK, trackId, params, Map.of());
    }

    /**
     * Creates an ADD_CLIP operation.
     */
    public static TimelineEditOperation addClip(String clipId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_CLIP, clipId, params, Map.of());
    }

    /**
     * Creates an ADD_CAPTION operation.
     */
    public static TimelineEditOperation addCaption(String captionId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_CAPTION, captionId, params, Map.of());
    }

    /**
     * Creates an ADD_WATERMARK operation.
     */
    public static TimelineEditOperation addWatermark(String watermarkId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_WATERMARK, watermarkId, params, Map.of());
    }

    /**
     * Creates an ADD_EFFECT operation.
     */
    public static TimelineEditOperation addEffect(String effectId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_EFFECT, effectId, params, Map.of());
    }

    /**
     * Creates an ADD_TRANSITION operation.
     */
    public static TimelineEditOperation addTransition(String transitionId, Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.ADD_TRANSITION, transitionId, params, Map.of());
    }

    /**
     * Creates a VALIDATE_TIMELINE operation.
     */
    public static TimelineEditOperation validateTimeline() {
        return new TimelineEditOperation(TimelineEditOperationType.VALIDATE_TIMELINE, null, Map.of(), Map.of());
    }

    /**
     * Creates an UPDATE_OUTPUT_PROFILE operation.
     */
    public static TimelineEditOperation updateOutputProfile(Map<String, Object> params) {
        return new TimelineEditOperation(TimelineEditOperationType.UPDATE_OUTPUT_PROFILE, null, params, Map.of());
    }
}
