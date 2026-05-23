package com.example.platform.render.domain.timeline;

import java.util.Map;

/**
 * External render segment (Blender / Remotion / Natron / VapourSynth).
 */
public record ExternalRenderNode(
        String id,
        String backend,
        String templateId,
        String graphId,
        String attachToClipId,
        double timelineStart,
        double duration,
        Map<String, Object> params,
        String intermediateFormat) {

    public static final String BACKEND_BLENDER = "blender";
    public static final String BACKEND_REMOTION = "remotion";
    public static final String BACKEND_NATRON = "natron";
    public static final String BACKEND_VAPOURSYNTH = "vapoursynth";
}
