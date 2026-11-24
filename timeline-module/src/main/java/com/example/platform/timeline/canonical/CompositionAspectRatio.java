package com.example.platform.timeline.canonical;

/** Authored composition space independent of preview and delivery rasters. */
public enum CompositionAspectRatio {
    LANDSCAPE_4_3(4, 3),
    PORTRAIT_3_4(3, 4);

    private final int width;
    private final int height;

    CompositionAspectRatio(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public static CompositionAspectRatio fromJson(com.fasterxml.jackson.databind.JsonNode value) {
        if (!value.isTextual()) throw new IllegalArgumentException("Composition aspect ratio must be a named value");
        return valueOf(value.textValue());
    }

    public boolean matches(int rasterWidth, int rasterHeight) {
        return (long) rasterWidth * height == (long) rasterHeight * width;
    }
}
