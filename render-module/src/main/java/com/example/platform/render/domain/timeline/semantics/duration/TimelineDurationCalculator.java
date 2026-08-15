package com.example.platform.render.domain.timeline.semantics.duration;

import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Timeline duration calculator — pure, deterministic, O(V).
 * <p>
 * Timeline Duration = maximum output end time of all duration-contributing entities.
 * Render pre-roll, post-roll, source handles are NOT counted in the timeline duration.
 */
public final class TimelineDurationCalculator {

    private TimelineDurationCalculator() {}

    /**
     * Calculates the total timeline duration.
     * O(V) where V = number of clips + transitions.
     */
    public static MediaTime calculateDuration(TimelineSemanticModel timeline) {
        Objects.requireNonNull(timeline, "timeline");
        MediaTime max = MediaTime.ZERO;

        for (MediaClip clip : timeline.clips()) {
            MediaTime clipEnd = clip.timelineRange().end();
            if (clipEnd.isGreaterThan(max)) {
                max = clipEnd;
            }
        }

        // Transitions with INSERT_DURATION policy add to timeline
        for (TransitionInstance t : timeline.transitions()) {
            if (t.temporalPolicy() == TransitionInstance.TransitionTemporalPolicy.INSERT_DURATION) {
                // The incoming clip end is shifted by transition duration
                Optional<MediaClip> incoming = timeline.clips().stream()
                    .filter(c -> c.clipId().equals(t.incomingClipId()))
                    .findFirst();
                if (incoming.isPresent()) {
                    MediaTime shiftedEnd = incoming.get().timelineRange().end().add(t.duration());
                    if (shiftedEnd.isGreaterThan(max)) {
                        max = shiftedEnd;
                    }
                }
            }
        }

        return max;
    }

    /**
     * Calculates the duration contribution of a single clip (including its effects' influence).
     */
    public static MediaTime clipContribution(MediaClip clip) {
        return clip.timelineDuration();
    }

    /**
     * Returns true if any effect in the timeline has VARIABLE_OUTPUT_DURATION behavior.
     */
    public static boolean hasVariableOutputDuration(TimelineSemanticModel timeline) {
        return timeline.effects().stream()
            .anyMatch(e -> {
                // Without effect definition lookup, we check the parameter "temporalBehavior"
                String tb = e.parameters().get("temporalBehavior");
                return "VARIABLE_OUTPUT_DURATION".equals(tb);
            });
    }

    /**
     * Temporal impact analysis result.
     *
     * @param beforeDuration        duration before the change
     * @param afterDuration         duration after the change
     * @param durationDelta         signed change in duration
     * @param affectedClipIds       clips affected by the change
     * @param shiftedClipIds        clips shifted (start time changed)
     * @param affectedTransitionIds transitions affected
     * @param affectedAutomationIds automations affected
     * @param affectedSubtitleCueIds subtitle cues affected (empty in this model)
     * @param renderDependencyRange the range needing re-render
     */
    public record TemporalImpact(
        MediaTime beforeDuration,
        MediaTime afterDuration,
        MediaTime durationDelta,
        List<String> affectedClipIds,
        List<String> shiftedClipIds,
        List<String> affectedTransitionIds,
        List<String> affectedAutomationIds,
        List<String> affectedSubtitleCueIds,
        MediaClip.TimeRange renderDependencyRange
    ) {}

    /**
     * Analyzes the temporal impact of a change to a clip's timeline range.
     * O(V + E) complexity.
     */
    public static TemporalImpact analyzeImpact(
            TimelineSemanticModel before,
            TimelineSemanticModel after,
            String changedClipId) {

        MediaTime beforeDur = calculateDuration(before);
        MediaTime afterDur = calculateDuration(after);
        MediaTime delta = afterDur.subtract(beforeDur);

        Set<String> affectedClips = new LinkedHashSet<>();
        Set<String> shiftedClips = new LinkedHashSet<>();
        Set<String> affectedTransitions = new LinkedHashSet<>();
        Set<String> affectedAutomations = new LinkedHashSet<>();

        // Find the changed clip
        MediaClip beforeClip = before.clips().stream()
            .filter(c -> c.clipId().equals(changedClipId)).findFirst().orElse(null);
        MediaClip afterClip = after.clips().stream()
            .filter(c -> c.clipId().equals(changedClipId)).findFirst().orElse(null);

        if (beforeClip != null && afterClip != null) {
            affectedClips.add(changedClipId);
            if (!beforeClip.timelineRange().start().isEqualTo(afterClip.timelineRange().start())) {
                shiftedClips.add(changedClipId);
            }
            // Find all clips that come after this one in the same track
            for (MediaClip c : after.clips()) {
                if (c.trackId().equals(afterClip.trackId()) &&
                    c.timelineRange().start().isGreaterThanOrEqualTo(beforeClip.timelineRange().start()) &&
                    !c.clipId().equals(changedClipId)) {
                    shiftedClips.add(c.clipId());
                }
            }
        }

        // Find affected transitions
        for (TransitionInstance t : after.transitions()) {
            if (t.outgoingClipId().equals(changedClipId) || t.incomingClipId().equals(changedClipId)) {
                affectedTransitions.add(t.transitionId());
            }
        }

        // Find affected automations targeting this clip
        for (Automation.AutomationCurve curve : after.automations()) {
            if (curve.targetEntityId().equals(changedClipId)) {
                affectedAutomations.add(curve.automationId());
            }
        }

        // Render dependency range: from the earliest affected point to the end
        MediaTime renderStart = beforeClip != null ? beforeClip.timelineRange().start() : MediaTime.ZERO;
        MediaTime renderEnd = afterDur;
        MediaClip.TimeRange renderRange = new MediaClip.TimeRange(renderStart, renderEnd);

        return new TemporalImpact(
            beforeDur, afterDur, delta,
            new ArrayList<>(affectedClips),
            new ArrayList<>(shiftedClips),
            new ArrayList<>(affectedTransitions),
            new ArrayList<>(affectedAutomations),
            List.of(), // no subtitle cues in this model
            renderRange
        );
    }

    /**
     * Pure function version: analyze impact of a hypothetical edit to a clip's timeline range.
     */
    public static TemporalImpact analyzeEditImpact(
            TimelineSemanticModel timeline,
            String clipId,
            MediaClip.TimeRange newRange) {

        MediaClip oldClip = timeline.clips().stream()
            .filter(c -> c.clipId().equals(clipId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Clip not found: " + clipId));

        List<MediaClip> newClips = timeline.clips().stream()
            .map(c -> c.clipId().equals(clipId)
                ? new MediaClip(c.clipId(), c.trackId(), newRange, c.sourceRange(), c.temporalMapping(), c.sourceBinding())
                : c)
            .toList();

        TimelineSemanticModel after = new TimelineSemanticModel(
            newClips, timeline.transitions(), timeline.effects(), timeline.automations(),
            timeline.schemaVersion());

        return analyzeImpact(timeline, after, clipId);
    }
}
