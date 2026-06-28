package com.example.platform.render.domain.template.composite;

/**
 * Merge policy for composite template child operations.
 * Internal domain model — vocabulary only, no merge execution.
 */
public enum CompositeTemplateMergePolicy {
    ORDERED,
    BY_LAYER,
    BY_TIMELINE_TIME,
    BY_Z_INDEX,
    MERGE_IF_COMPATIBLE
}
