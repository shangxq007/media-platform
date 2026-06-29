package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Identifier for a merge plan.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePlanId(String value) {

    public TimelineMergePlanId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineMergePlanId must not be blank");
    }
}
