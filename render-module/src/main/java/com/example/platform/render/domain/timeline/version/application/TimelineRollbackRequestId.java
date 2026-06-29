package com.example.platform.render.domain.timeline.version.application;

/**
 * Identifier for a timeline rollback request.
 * Internal domain model.
 */
public record TimelineRollbackRequestId(String value) {
    public TimelineRollbackRequestId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineRollbackRequestId must not be blank");
    }
}
