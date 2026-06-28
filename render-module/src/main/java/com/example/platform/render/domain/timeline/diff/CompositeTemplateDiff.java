package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Diff bridge for composite template changes.
 * Internal domain model. Does not expand or execute composite templates.
 */
public record CompositeTemplateDiff(
        String compositeTemplateId,
        List<String> childTemplateIds,
        List<TimelineChangeOperation> operations,
        Map<String, String> safeMetadata) {}
