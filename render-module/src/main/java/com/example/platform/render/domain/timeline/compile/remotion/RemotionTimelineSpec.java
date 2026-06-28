package com.example.platform.render.domain.timeline.compile.remotion;

import java.util.List;

/**
 * Remotion timeline structure.
 * Internal only.
 */
public record RemotionTimelineSpec(
        List<RemotionTrackSpec> tracks,
        double totalDurationSeconds) {}
