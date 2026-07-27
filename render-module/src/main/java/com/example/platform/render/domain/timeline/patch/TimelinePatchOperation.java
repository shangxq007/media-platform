package com.example.platform.render.domain.timeline.patch;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;

/**
 * Sealed hierarchy of strongly typed patch operations.
 * Each operation carries all data needed for execution.
 */
public sealed interface TimelinePatchOperation {

    String operationId();

    // ==================== Track Operations ====================

    record AddTrack(
            String operationId,
            TimelineTrack track,
            int targetPosition) implements TimelinePatchOperation {}

    record RemoveTrack(
            String operationId,
            String trackId) implements TimelinePatchOperation {}

    record UpdateTrackProperty(
            String operationId,
            String trackId,
            String property,
            String expectedBefore,
            String newValue) implements TimelinePatchOperation {}

    record ReorderTrack(
            String operationId,
            String trackId,
            int targetPosition) implements TimelinePatchOperation {}

    // ==================== Clip Operations ====================

    record AddClip(
            String operationId,
            String targetTrackId,
            TimelineClip clip,
            int targetPosition) implements TimelinePatchOperation {}

    record RemoveClip(
            String operationId,
            String clipId,
            String expectedTrackId) implements TimelinePatchOperation {}

    record UpdateClipProperty(
            String operationId,
            String clipId,
            String property,
            String expectedBefore,
            String newValue) implements TimelinePatchOperation {}

    record MoveClip(
            String operationId,
            String clipId,
            String expectedSourceTrackId,
            String targetTrackId,
            int targetPosition) implements TimelinePatchOperation {}

    record ReorderClip(
            String operationId,
            String clipId,
            String trackId,
            int targetPosition) implements TimelinePatchOperation {}
}
