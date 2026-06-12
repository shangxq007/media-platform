package com.example.platform.render.infrastructure.effects;

/**
 * Backend implementation types for effects.
 */
public enum EffectBackendKind {
    /** Native FFmpeg filter */
    NATIVE_FFMPEG,
    
    /** Native libass rendering */
    NATIVE_LIBASS,
    
    /** Java2D/AWT rendering */
    JAVA2D,
    
    /** OpenFX plugin */
    OPENFX,
    
    /** Remotion JavaScript rendering */
    REMOTION,
    
    /** Skia rendering engine */
    SKIA,
    
    /** Packaging tools (GPAC, Bento4, etc.) */
    PACKAGING,
    
    /** Internal platform logic */
    INTERNAL
}
