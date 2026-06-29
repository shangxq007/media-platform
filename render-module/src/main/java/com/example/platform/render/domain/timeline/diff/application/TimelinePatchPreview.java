package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.render.domain.timeline.diff.TimelineRenderImpact;
import java.util.List;
import java.util.Map;

/**
 * Preview of a patch application without applying. Internal domain model.
 */
public record TimelinePatchPreview(
        String baseRevisionId,
        int operationCount,
        int supportedOperationCount,
        int unsupportedOperationCount,
        TimelineRenderImpact estimatedRenderImpact,
        List<TimelinePatchApplicationIssue> issues,
        TimelinePatchPreviewStatus status,
        Map<String, String> safeMetadata) {}
