package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.List;
import java.util.Map;

/**
 * Canonical timeline snapshot for diff input.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record CanonicalTimelineSnapshot(
        CanonicalTimelineSnapshotId id,
        String revisionId,
        long durationMs,
        List<CanonicalTimelineTrackSnapshot> tracks,
        List<CanonicalTimelineCaptionSnapshot> captions,
        List<CanonicalTimelineWatermarkSnapshot> watermarks,
        List<CanonicalTimelineTemplateApplicationSnapshot> templateApplications,
        List<CanonicalTimelineWorkflowStepSnapshot> workflowSteps,
        CanonicalTimelineOutputProfileSnapshot outputProfile,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineSnapshot {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (revisionId == null || revisionId.isBlank())
            throw new IllegalArgumentException("revisionId must not be blank");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be non-negative");
    }
}
