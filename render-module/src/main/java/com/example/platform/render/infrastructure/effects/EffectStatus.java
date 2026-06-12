package com.example.platform.render.infrastructure.effects;

/**
 * Implementation status of an effect.
 */
public enum EffectStatus {
    /** Fully implemented and production-ready */
    IMPLEMENTED,
    
    /** Partially implemented, may have limitations */
    PARTIAL,
    
    /** Proof of concept, not for production */
    POC,
    
    /** Stub implementation, not functional */
    STUB,
    
    /** Planned for future implementation */
    PLANNED,
    
    /** Deprecated, will be removed */
    DEPRECATED
}
