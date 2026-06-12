package com.example.platform.render.infrastructure.effects;

/**
 * Target types where effects can be applied in the timeline/render pipeline.
 */
public enum EffectTargetType {
    /** Effect applies to the entire timeline */
    TIMELINE,
    
    /** Effect applies to a specific track */
    TRACK,
    
    /** Effect applies to a single clip */
    CLIP,
    
    /** Effect applies to a range of clips */
    CLIP_RANGE,
    
    /** Effect applies to a transition edge between clips */
    TRANSITION_EDGE,
    
    /** Effect applies to an overlay layer */
    OVERLAY_LAYER,
    
    /** Effect applies to a text layer */
    TEXT_LAYER,
    
    /** Effect applies to a subtitle track */
    SUBTITLE_TRACK,
    
    /** Effect applies to an audio track */
    AUDIO_TRACK,
    
    /** Effect applies to the final output */
    OUTPUT
}
