package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Diff bridge for template application changes.
 * Internal domain model. Does not execute templates.
 */
public record TemplateApplicationDiff(
        String templateApplicationId,
        List<TimelineChangeOperation> operations,
        Map<String, String> safeMetadata) {}
