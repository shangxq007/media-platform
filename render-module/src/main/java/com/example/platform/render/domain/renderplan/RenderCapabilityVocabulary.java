package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory;
import java.util.Objects;

/**
 * ROADMAP20 correction F3: bounded deterministic mapping from authored render
 * semantics to the platform capability authority (ROADMAP_16).
 *
 * <p>Render is allowed to own a bounded MAPPING from authored render semantics
 * to {@link CapabilityRequirement}; it is NOT allowed to own a competing
 * capability identity/namespace authority. All capability ids produced here are
 * platform-reserved {@link CapabilityId} values (video.*, audio.*,
 * subtitle.*, render.*), version-aware via {@link ContractVersionRange}.
 *
 * <p>Never encodes provider/plugin/worker/device/tier/plan identity.
 */
public final class RenderCapabilityVocabulary {

    /** Contract version of the platform capability vocabulary consumed by #20. */
    private static final ContractVersion CONTRACT_1_0 = ContractVersion.of(1, 0);

    private RenderCapabilityVocabulary() {
    }

    private static CapabilityRequirement requirement(CapabilityId id) {
        return CapabilityRequirement.of(id, ContractVersionRange.exactly(CONTRACT_1_0));
    }

    /** DECODE node: video decode capability. */
    public static CapabilityRequirement videoDecode() {
        return requirement(CapabilityId.of("video.decode"));
    }

    /**
     * R6-B: lowers an authored {@code EffectDefinition.requiredCapabilities}
     * entry (a capability identity String) into a typed platform
     * {@link CapabilityRequirement} via the platform capability authority
     * ({@link CapabilityId#of} validation + exact platform contract 1.0).
     *
     * <p>Bounded lowering rule (R6-B2): the authored String is interpreted as a
     * {@code CapabilityId}; if it does not parse as a valid platform capability
     * id this FAILS CLOSED (no raw-string capability authority in render, no
     * invented per-provider semantics). The contract range is the exact
     * platform contract 1.0 — a documented R6 bounded default, not a
     * per-provider invention.
     *
     * @param requiredCapability authored capability identity String
     * @return typed capability requirement
     * @throws IllegalArgumentException if the string is not a valid CapabilityId
     */
    public static CapabilityRequirement forRequiredCapability(String requiredCapability) {
        Objects.requireNonNull(requiredCapability, "requiredCapability");
        return requirement(CapabilityId.of(requiredCapability));
    }

    /**
     * R6-B: effective capability requirements for an EFFECT node =
     * category baseline capability UNION definition-required capabilities
     * (deduplicated by capability id, deterministic order).
     *
     * <p>The category mapping is the Render-owned bounded baseline; the
     * definition's authored {@code requiredCapabilities} are the authoritative
     * definition semantics and MUST NOT be replaced by the category mapping.
     */
    public static java.util.List<CapabilityRequirement> forEffect(
            EffectCategory category,
            java.util.List<String> definitionRequiredCapabilities) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(definitionRequiredCapabilities, "definitionRequiredCapabilities");
        // deterministic union: category baseline FIRST, then definition-required
        // capabilities, deduplicated by capability id preserving first-seen order
        // (no map token in the renderplan package — C20 guard).
        java.util.ArrayList<CapabilityRequirement> result = new java.util.ArrayList<>();
        java.util.ArrayList<String> seen = new java.util.ArrayList<>();
        CapabilityRequirement baseline = forEffect(category);
        result.add(baseline);
        seen.add(baseline.capabilityId().value());
        for (String required : definitionRequiredCapabilities) {
            CapabilityRequirement lowered = forRequiredCapability(required);
            if (!seen.contains(lowered.capabilityId().value())) {
                seen.add(lowered.capabilityId().value());
                result.add(lowered);
            }
        }
        return java.util.List.copyOf(result);
    }

    /** EFFECT node: maps an authoritative effect category to a platform capability. */
    public static CapabilityRequirement forEffect(EffectCategory category) {
        Objects.requireNonNull(category, "category");
        return switch (category) {
            case TRANSFORM -> requirement(CapabilityId.of("video.effect.transform"));
            case CROP -> requirement(CapabilityId.of("video.effect.crop"));
            case OPACITY -> requirement(CapabilityId.of("video.effect.opacity"));
            case BLEND_MODE -> requirement(CapabilityId.of("video.effect.blend-mode"));
            case COLOR_ADJUSTMENT -> requirement(CapabilityId.of("video.effect.color-adjustment"));
            case GAUSSIAN_BLUR -> requirement(CapabilityId.of("video.effect.gaussian-blur"));
            case FADE -> requirement(CapabilityId.of("video.effect.fade"));
            case GAIN -> requirement(CapabilityId.of("audio.effect.gain"));
            case PAN -> requirement(CapabilityId.of("audio.effect.pan"));
            case EQUALIZER -> requirement(CapabilityId.of("audio.effect.equalizer"));
            case COMPRESSOR -> requirement(CapabilityId.of("audio.effect.compressor"));
            case LIMITER -> requirement(CapabilityId.of("audio.effect.limiter"));
        };
    }

    /** AUDIO_PROCESS node: audio processing capability. */
    public static CapabilityRequirement audioProcess() {
        return requirement(CapabilityId.of("audio.process"));
    }

    /** AUDIO_MIX node: audio mixing capability. */
    public static CapabilityRequirement audioMix() {
        return requirement(CapabilityId.of("audio.mix"));
    }

    /** TIMED_TEXT node: subtitle/text rasterization capability. */
    public static CapabilityRequirement timedTextRasterize() {
        return requirement(CapabilityId.of("subtitle.rasterize"));
    }

    /** COMPOSITE node: visual composition capability. */
    public static CapabilityRequirement composite() {
        return requirement(CapabilityId.of("render.composite"));
    }

    /** OUTPUT node: output encoding capability. */
    public static CapabilityRequirement outputEncode() {
        return requirement(CapabilityId.of("render.output"));
    }
}
