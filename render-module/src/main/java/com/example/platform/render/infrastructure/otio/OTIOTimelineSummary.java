package com.example.platform.render.infrastructure.otio;

import java.util.List;
import java.util.Map;

public record OTIOTimelineSummary(
        String schemaVersion,
        String projectId,
        String timelineId,
        double duration,
        int videoTrackCount,
        int audioTrackCount,
        int subtitleTrackCount,
        List<OTIOTrackSummary> videoTracks,
        List<OTIOTrackSummary> audioTracks,
        List<OTIOTrackSummary> subtitleTracks,
        List<OTIOCaptionRef> captionRefs,
        List<OTIOFontRef> fontRefs,
        List<OTIOTemplateRef> templateRefs,
        List<OTIOEffectRef> effectRefs,
        OTIORenderHints renderHints
) {}
