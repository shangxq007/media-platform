package com.example.platform.timeline.diff.merge;

/**
 * Request to perform a three-way merge between two revision branches.
 */
public record TimelineMergeRequest(
        com.example.platform.timeline.app.TimelineMutationContext mutationContext,
        String baseRevisionId,
        String sourceRevisionId,
        String targetRevisionId,
        String message) {

    public static final String SOURCE_MERGE = "merge";

    public TimelineMergeRequest {
        if (mutationContext == null) {
            throw new IllegalArgumentException("mutationContext required");
        }
    }

    public String projectId() {
        return mutationContext.projectId();
    }

    public String tenantId() {
        return mutationContext.tenantId();
    }

    public String effectiveTenant() {
        return mutationContext.tenantId();
    }
}
