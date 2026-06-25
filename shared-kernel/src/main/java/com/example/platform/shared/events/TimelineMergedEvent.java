package com.example.platform.shared.events;

/**
 * Published when two timeline branches are merged successfully.
 */
public record TimelineMergedEvent(
        String projectId,
        String baseRevisionId,
        String sourceRevisionId,
        String targetRevisionId,
        String mergeRevisionId,
        String mergeParentRevisionIds,
        String mergeBaseRevisionId) {}
