package com.example.platform.render.domain.visual;

/**
 * Parameter type for visual capability parameters.
 * Immutable enum. Internal domain model.
 */
public enum VisualCapabilityParameterType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    DURATION_MS,
    POSITION_2D,
    COLOR_RGBA,
    ENUM,
    FILE_PATH
}
