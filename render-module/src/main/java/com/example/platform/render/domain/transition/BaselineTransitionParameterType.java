package com.example.platform.render.domain.transition;

/**
 * Parameter types for baseline transition operations.
 * Immutable enum. Internal domain model.
 */
public enum BaselineTransitionParameterType {
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
