package com.example.platform.render.domain.plan;

/**
 * Parameter types for render steps.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderStepParameterType {
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
