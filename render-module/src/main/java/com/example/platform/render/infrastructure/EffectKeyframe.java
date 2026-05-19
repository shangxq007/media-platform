package com.example.platform.render.infrastructure;

public record EffectKeyframe(
        double timeSec,
        Object value,
        String easing
) {}
