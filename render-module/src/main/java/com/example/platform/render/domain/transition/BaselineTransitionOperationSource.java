package com.example.platform.render.domain.transition;

/**
 * Source of a baseline transition operation.
 * Immutable enum. Internal domain model.
 */
public enum BaselineTransitionOperationSource {
    BASIC_TIMELINE_TRANSITION_REF,
    VISUAL_CAPABILITY_RESOLVED,
    POLICY_DEFAULT,
    INTERNAL_ANNOTATION
}
