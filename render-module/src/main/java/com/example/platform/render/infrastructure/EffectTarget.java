package com.example.platform.render.infrastructure;

public record EffectTarget(
        String trackId,
        String clipId,
        double[] rangeSec
) {}
