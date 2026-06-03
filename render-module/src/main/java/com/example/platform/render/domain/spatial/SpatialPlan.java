package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpatialPlan(
        @JsonProperty("$schema") String schemaVersion,
        String version,
        String projectId,
        String description,
        CoordinateSystem coordinateSystem,
        CanvasConfig canvas,
        List<SpatialOperation> operations,
        RoundingPolicy roundingPolicy
) {
    public static final int DEFAULT_CANVAS_WIDTH = 1920;
    public static final int DEFAULT_CANVAS_HEIGHT = 1080;

    public int canvasWidth() {
        return canvas != null ? canvas.width() : DEFAULT_CANVAS_WIDTH;
    }

    public int canvasHeight() {
        return canvas != null ? canvas.height() : DEFAULT_CANVAS_HEIGHT;
    }
}
