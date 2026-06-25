package com.example.platform.render.domain.timeline.internal;

/**
 * Request to perform a three-way merge between two revision branches.
 */
public record TimelineMergeRequest(
        String projectId,
        String tenantId,
        String baseRevisionId,
        String sourceRevisionId,
        String targetRevisionId,
        String authorUserId,
        String message) {

    public static final String SOURCE_MERGE = "merge";

    public String effectiveTenant() {
        return tenantId != null ? tenantId : "";
    }
}
