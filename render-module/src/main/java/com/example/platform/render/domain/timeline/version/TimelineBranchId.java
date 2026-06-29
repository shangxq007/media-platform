package com.example.platform.render.domain.timeline.version;

/**
 * Identifier for a timeline branch.
 * Internal domain model.
 */
public record TimelineBranchId(String value) {
    public TimelineBranchId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineBranchId must not be blank");
    }
}
