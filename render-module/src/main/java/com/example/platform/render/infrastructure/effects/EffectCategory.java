package com.example.platform.render.infrastructure.effects;

/**
 * Categories for classifying visual/audio effects in the render pipeline.
 */
public enum EffectCategory {
    /** Visual filter effects (blur, sharpen, vignette, etc.) */
    FILTER,
    
    /** Color grading and correction effects */
    COLOR,
    
    /** Geometric transform effects (scale, rotate, crop) */
    TRANSFORM,
    
    /** Transition effects between clips (fade, dissolve, wipe, etc.) */
    TRANSITION,
    
    /** Image/video overlay effects (stickers, watermarks, PIP) */
    OVERLAY,
    
    /** Text overlay effects */
    TEXT,
    
    /** Subtitle burn-in and rendering */
    SUBTITLE,
    
    /** Audio processing effects */
    AUDIO,
    
    /** Multi-track composition and layering */
    COMPOSITION,
    
    /** Output packaging effects (DASH, HLS, DRM) */
    PACKAGING
}
