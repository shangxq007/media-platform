package com.example.platform.render.domain.timeline.version.application;

/**
 * Identifier for a timeline checkout request.
 * Internal domain model.
 */
public record TimelineCheckoutRequestId(String value) {
    public TimelineCheckoutRequestId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineCheckoutRequestId must not be blank");
    }
}
