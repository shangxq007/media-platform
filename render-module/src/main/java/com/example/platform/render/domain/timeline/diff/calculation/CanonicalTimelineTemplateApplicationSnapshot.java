package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Template application snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineTemplateApplicationSnapshot(
        String templateApplicationId,
        String templateId,
        String templateVersion,
        Map<String, String> parameters,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineTemplateApplicationSnapshot {
        if (templateApplicationId == null || templateApplicationId.isBlank())
            throw new IllegalArgumentException("templateApplicationId must not be blank");
        if (templateId == null || templateId.isBlank())
            throw new IllegalArgumentException("templateId must not be blank");
    }
}
