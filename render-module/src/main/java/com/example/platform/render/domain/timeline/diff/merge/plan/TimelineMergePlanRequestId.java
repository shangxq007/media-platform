package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Identifier for a merge plan request.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePlanRequestId(String value) {

    public TimelineMergePlanRequestId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineMergePlanRequestId must not be blank");
    }
}
