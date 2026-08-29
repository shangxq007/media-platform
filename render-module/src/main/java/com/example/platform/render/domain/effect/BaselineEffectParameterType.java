package com.example.platform.render.domain.effect;

/**
 * Parameter types for baseline effect operations.
 * Immutable enum. Internal domain model.
 */
public enum BaselineEffectParameterType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DURATION_MS,
    PERCENT,
    PIXEL,
    RATIO,
    ENUM,
    COLOR,
    SAFE_REF
}
