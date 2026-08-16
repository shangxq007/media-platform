package com.example.platform.timeline.semantics.validation;

import com.example.platform.timeline.semantics.automation.Automation;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.transition.TransitionInstance;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate model for the timeline semantics validator.
 * Immutable container for all semantic entities in a timeline.
 *
 * @param clips        ordered clips within tracks
 * @param transitions  transition instances connecting clips
 * @param effects      effect instances applied to clips
 * @param automations  automation curves
 * @param schemaVersion payload schema version
 */
public record TimelineSemanticModel(
    List<MediaClip> clips,
    List<TransitionInstance> transitions,
    List<EffectInstance> effects,
    List<Automation.AutomationCurve> automations,
    String schemaVersion
) {
    public TimelineSemanticModel {
        Objects.requireNonNull(clips, "clips");
        Objects.requireNonNull(transitions, "transitions");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(automations, "automations");
        if (clips.isEmpty()) clips = List.of();
        else clips = List.copyOf(clips);
        if (transitions.isEmpty()) transitions = List.of();
        else transitions = List.copyOf(transitions);
        if (effects.isEmpty()) effects = List.of();
        else effects = List.copyOf(effects);
        if (automations.isEmpty()) automations = List.of();
        else automations = List.copyOf(automations);
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = "timeline-semantics-v1";
        }
    }

    public static TimelineSemanticModel empty() {
        return new TimelineSemanticModel(List.of(), List.of(), List.of(), List.of(), "timeline-semantics-v1");
    }
}
