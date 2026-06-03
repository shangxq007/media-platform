package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpatialOperation(
        String id,
        String type,
        String description,
        String status,
        String reason,
        SpatialSource source,
        PpmRegion region,
        PpmPosition position,
        Double opacity,
        String blendMode,
        String space,
        SafeAreaInsets insets,
        Boolean clampToFrame
) {
    public boolean isSupported() {
        return "supported".equals(status);
    }
    public boolean isCrop() {
        return "crop".equals(type);
    }
    public boolean isOverlay() {
        return "overlay".equals(type) || "composite".equals(type);
    }
    public boolean isTransform() {
        return "transform".equals(type) || "placement".equals(type);
    }
}
