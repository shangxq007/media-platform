package com.example.platform.render.domain.renderplan;

/**
 * Bounded provider-neutral capability vocabulary (C17). Derived from canonical
 * authored semantics (EffectCategory, decode/mix/raster/encode operations);
 * NEVER provider names, plugin names, or subscription tiers.
 */
public enum RenderCapabilityId {
    DECODE,
    EFFECT_TRANSFORM,
    EFFECT_CROP,
    EFFECT_OPACITY,
    EFFECT_BLEND_MODE,
    EFFECT_COLOR_ADJUSTMENT,
    EFFECT_GAUSSIAN_BLUR,
    EFFECT_FADE,
    AUDIO_PROCESS,
    MIX_AUDIO,
    RASTERIZE_TIMED_TEXT,
    COMPOSITE_IMAGE,
    OUTPUT_ENCODE
}
