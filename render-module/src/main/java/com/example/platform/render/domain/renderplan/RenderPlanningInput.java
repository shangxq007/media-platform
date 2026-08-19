package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.util.List;
import java.util.Objects;

/**
 * Immutable planning input: one immutable TimelineRevision + authored semantics +
 * RenderRequest + resolution state + capability context (C16). List.copyOf
 * everywhere. Resolution state, capability context, and the effect-definition
 * catalog are NOT fingerprint inputs (their effect flows through node
 * requirements/identity, C7).
 */
public record RenderPlanningInput(
        TimelineRevisionReference revision,
        List<MediaClip> clips,
        List<EffectInstance> effects,
        List<EffectInstance.EffectDefinition> effectDefinitions,
        AudioMix audioMix,
        List<TextElement> textElements,
        RenderRequest request,
        SourceResolutionInput resolution,
        CapabilityContext capabilities) {

    public RenderPlanningInput {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(clips, "clips");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");
        Objects.requireNonNull(audioMix, "audioMix");
        Objects.requireNonNull(textElements, "textElements");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(capabilities, "capabilities");
        clips = List.copyOf(clips);
        effects = List.copyOf(effects);
        effectDefinitions = List.copyOf(effectDefinitions);
        textElements = List.copyOf(textElements);
    }
}
