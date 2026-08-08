package com.example.platform.workflow.definition.domain;

import java.time.Instant;

/**
 * Publication audit state; present only while the version is PUBLISHED.
 */
public record UserWorkflowPublicationState(Instant publishedAt, String publishedBy) {

    public UserWorkflowPublicationState {
        if (publishedAt == null || publishedBy == null || publishedBy.isBlank()) {
            throw new IllegalArgumentException("publication state requires publishedAt and publishedBy");
        }
    }
}
