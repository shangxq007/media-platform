package com.example.platform.render.infrastructure;

public record RenderConstraints(
        int maxWidth,
        int maxHeight,
        int maxFrameRate,
        int maxDurationSec,
        String requiredFormat,
        String requiredCodec
) {}
