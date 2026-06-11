package com.example.platform.render.infrastructure.otio;

public record OTIOCaptionRef(
        String captionId,
        String assetRef,
        double startTime,
        double endTime,
        String styleRef,
        String templateRef
) {}
