package com.example.platform.render.domain.timeline.version.application;

/**
 * Identifier for a timeline branch switch request.
 * Internal domain model.
 */
public record TimelineBranchSwitchRequestId(String value) {
    public TimelineBranchSwitchRequestId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineBranchSwitchRequestId must not be blank");
    }
}
