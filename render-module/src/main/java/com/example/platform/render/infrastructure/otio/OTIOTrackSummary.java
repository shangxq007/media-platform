package com.example.platform.render.infrastructure.otio;

import java.util.List;

public record OTIOTrackSummary(
        String trackId,
        String trackName,
        String trackType,
        List<OTIOClipSummary> clips
) {}
