package com.example.platform.render.domain.effect;

/**
 * Source of a baseline effect operation.
 * Immutable enum. Internal domain model.
 */
public enum BaselineEffectOperationSource {
    BASIC_TIMELINE_EFFECT_REF,
    VISUAL_CAPABILITY_RESOLVED,
    POLICY_DEFAULT,
    INTERNAL_ANNOTATION
}
