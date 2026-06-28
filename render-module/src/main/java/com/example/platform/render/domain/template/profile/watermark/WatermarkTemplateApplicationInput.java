package com.example.platform.render.domain.template.profile.watermark;

import java.util.Map;

/**
 * Input for Watermark Template application.
 *
 * <p>Internal domain model — not a public API DTO.</p>
 *
 * <p>No raw URLs, storage internals, or provider fields.</p>
 *
 * @param projectId          project identifier (required)
 * @param mainVideoProductId source video Product ID (required)
 * @param watermarkProductId watermark image/logo Product ID (required)
 * @param placement          placement position (e.g., "BOTTOM_RIGHT", "TOP_LEFT")
 * @param opacityPercent     opacity 0–100 (default 50)
 * @param marginX            horizontal margin in pixels (default 0)
 * @param marginY            vertical margin in pixels (default 0)
 * @param safeMetadata       safe metadata only
 */
public record WatermarkTemplateApplicationInput(
        String projectId,
        String mainVideoProductId,
        String watermarkProductId,
        String placement,
        Integer opacityPercent,
        Integer marginX,
        Integer marginY,
        Map<String, String> safeMetadata) {

    public String effectivePlacement() {
        return placement != null && !placement.isBlank() ? placement : "BOTTOM_RIGHT";
    }

    public int effectiveOpacity() {
        return opacityPercent != null ? opacityPercent : 50;
    }

    public int effectiveMarginX() {
        return marginX != null ? marginX : 0;
    }

    public int effectiveMarginY() {
        return marginY != null ? marginY : 0;
    }
}
