package com.example.platform.render.infrastructure.otio;

public record OTIOClipSummary(
        String clipId,
        String clipName,
        String assetRef,
        double timelineStart,
        double timelineDuration,
        double sourceStart,
        double sourceDuration,
        String mediaType
) {}
