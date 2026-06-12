package com.example.platform.render.infrastructure.effects;

/**
 * Pipeline stages where effects can be applied.
 */
public enum EffectStage {
    /** Preprocessing stage (input validation, normalization) */
    PREPROCESS,
    
    /** Individual clip filter application */
    CLIP_FILTER,
    
    /** Track-level composition and layering */
    TRACK_COMPOSITION,
    
    /** Timeline-level composition */
    TIMELINE_COMPOSITION,
    
    /** Subtitle burn-in rendering */
    SUBTITLE_BURN_IN,
    
    /** Text overlay rendering */
    TEXT_RENDER,
    
    /** Output packaging (DASH, HLS, etc.) */
    PACKAGING,
    
    /** Post-processing stage (final touches) */
    POSTPROCESS
}
