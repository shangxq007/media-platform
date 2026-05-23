package com.example.platform.render.domain.timeline;

/**
 * Transition between clips (maps to OTIO Transition).
 */
public record TimelineTransition(
        String id,
        String effectKey,
        double durationSeconds,
        String inClipId,
        String outClipId) {
}
