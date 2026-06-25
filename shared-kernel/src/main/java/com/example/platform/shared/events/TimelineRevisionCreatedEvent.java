package com.example.platform.shared.events;

/**
 * Published when a new timeline revision is created.
 */
public record TimelineRevisionCreatedEvent(
        String projectId,
        String revisionId,
        int revisionNumber,
        String author,
        String source,
        String summary,
        boolean isMerge) {}
