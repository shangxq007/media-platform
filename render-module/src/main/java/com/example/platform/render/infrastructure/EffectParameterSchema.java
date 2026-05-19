package com.example.platform.render.infrastructure;

public record EffectParameterSchema(
        String name,
        String type,
        Object defaultValue,
        Object min,
        Object max,
        String description
) {}
