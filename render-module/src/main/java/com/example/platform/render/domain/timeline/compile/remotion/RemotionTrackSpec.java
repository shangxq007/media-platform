package com.example.platform.render.domain.timeline.compile.remotion;

import java.util.List;

/**
 * Remotion track specification.
 * Internal only.
 */
public record RemotionTrackSpec(
        String trackId,
        String name,
        String type,
        int layer,
        boolean muted,
        List<RemotionClipSpec> clips) {}
