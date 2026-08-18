package com.example.platform.timeline.diff.calculation;

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
 *
 * <p>THIRD CORRECTION (semantic state preservation): the full 12-field
 * constructor is the ONLY constructor. Every reconstruction must carry
 * transitions and automations explicitly — silent field loss is impossible.
 * Copy helpers (withTracks/withDuration/withTransitions/withAutomations/
 * withMetadata) preserve every unrelated field.
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
        Map<String, String> safeMetadata,
        List<com.example.platform.timeline.canonical.TextElement> textElements,
        List<CanonicalTimelineTransitionSnapshot> transitions,
        List<CanonicalTimelineAutomationSnapshot> automations) {

    public CanonicalTimelineSnapshot {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (revisionId == null || revisionId.isBlank())
            throw new IllegalArgumentException("revisionId must not be blank");
        Objects.requireNonNull(duration, "duration");
        // MediaTime is non-negative by construction.
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        automations = automations == null ? List.of() : List.copyOf(automations);
    }

    // ── Full-state copy helpers (THIRD CORRECTION): every helper preserves all
    //    unrelated fields, including transitions and automations. ──

    public CanonicalTimelineSnapshot withTracks(List<CanonicalTimelineTrackSnapshot> tracks) {
        return new CanonicalTimelineSnapshot(id, revisionId, duration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, safeMetadata, textElements, transitions, automations);
    }

    public CanonicalTimelineSnapshot withDuration(MediaTime newDuration) {
        return new CanonicalTimelineSnapshot(id, revisionId, newDuration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, safeMetadata, textElements, transitions, automations);
    }

    // ROADMAP #19: TimedText-aware full-state copy helper (preserves all other
    // fields, including effects via tracks, transitions and automations).
    public CanonicalTimelineSnapshot withTextElements(List<com.example.platform.timeline.canonical.TextElement> newTextElements) {
        return new CanonicalTimelineSnapshot(id, revisionId, duration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, safeMetadata, newTextElements, transitions, automations);
    }

    public CanonicalTimelineSnapshot withTransitions(List<CanonicalTimelineTransitionSnapshot> newTransitions) {
        return new CanonicalTimelineSnapshot(id, revisionId, duration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, safeMetadata, textElements, newTransitions, automations);
    }

    public CanonicalTimelineSnapshot withAutomations(List<CanonicalTimelineAutomationSnapshot> newAutomations) {
        return new CanonicalTimelineSnapshot(id, revisionId, duration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, safeMetadata, textElements, transitions, newAutomations);
    }

    public CanonicalTimelineSnapshot withMetadata(Map<String, String> newMetadata) {
        return new CanonicalTimelineSnapshot(id, revisionId, duration,
                tracks, captions, watermarks, templateApplications, workflowSteps,
                outputProfile, newMetadata, textElements, transitions, automations);
    }
}
