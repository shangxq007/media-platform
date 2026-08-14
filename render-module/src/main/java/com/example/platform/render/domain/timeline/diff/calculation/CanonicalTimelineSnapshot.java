package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.shared.time.MediaTime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical timeline snapshot for diff input (C1-CNM1 exact-time contract).
 *
 * <p>{@code duration} is an exact {@link MediaTime}; integer milliseconds
 * are a projection, never merge semantic authority.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record CanonicalTimelineSnapshot(
        CanonicalTimelineSnapshotId id,
        String revisionId,
        MediaTime duration,
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
        Objects.requireNonNull(duration, "duration");
        // MediaTime is non-negative by construction.
    }
}
