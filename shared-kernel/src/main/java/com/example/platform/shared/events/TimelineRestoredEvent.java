package com.example.platform.shared.events;

/**
 * Published when a timeline is restored to a previous revision.
 */
public record TimelineRestoredEvent(
        String projectId,
        String restoredFromRevisionId,
        String newRevisionId) {}
