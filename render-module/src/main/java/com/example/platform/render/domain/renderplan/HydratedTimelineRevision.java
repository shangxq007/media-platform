package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction F4: coherent immutable hydrated revision projection.
 *
 * <p>Integrity-binds the Timeline revision identity + content digest with the
 * authored semantic projection consumed by the render materializer (clips,
 * effects, effect definitions, audio mix, text elements). Represents ONE
 * coherent immutable revision projection — callers cannot casually mix a
 * revision reference R1 with authored fragments from R2/R3 through the primary
 * planning API.
 *
 * <p>The pure render planner consumes this object plus {@link RenderRequest}
 * and transient planning context. Hydration/validation of the revision (load +
 * digest revalidation) happens at the application/adapter layer; the pure
 * domain planner never queries repositories and never loads mutable
 * "latest" canonical state.
 *
 * @param revision      immutable revision identity + content digest pin
 * @param clips         authored clip projection from THIS revision
 * @param effects       authored effect instances from THIS revision
 * @param effectDefinitions effect definition catalog (authoritative category source)
 * @param audioMix      authored audio mix from THIS revision
 * @param textElements  authored text elements from THIS revision
 */
public record HydratedTimelineRevision(
        TimelineRevisionReference revision,
        List<MediaClip> clips,
        List<EffectInstance> effects,
        List<EffectInstance.EffectDefinition> effectDefinitions,
        AudioMix audioMix,
        List<TextElement> textElements) {

    public HydratedTimelineRevision {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(clips, "clips");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");
        Objects.requireNonNull(audioMix, "audioMix");
        Objects.requireNonNull(textElements, "textElements");
        clips = List.copyOf(clips);
        effects = List.copyOf(effects);
        effectDefinitions = List.copyOf(effectDefinitions);
        textElements = List.copyOf(textElements);
    }

    public TimelineRevisionReference revision() {
        return revision;
    }

    /** Content digest pin of the immutable revision (provenance/fingerprint). */
    public ContentDigest contentDigest() {
        return revision.contentDigest();
    }
}
