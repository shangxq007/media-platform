package com.example.platform.workflow.temporal.mediatask;

import java.util.List;
import java.util.Objects;

/**
 * Stable result of one media task workflow run.
 *
 * <p>Only identities and canonical digests: the artifacts produced by the graph, referenced by
 * digest, plus the workflow status. No graph JSON, no Artifact bytes, no proof material.
 */
public record MediaTaskWorkflowResult(
        String tenantId,
        String renderJobId,
        String planDigest,
        String expectedExecutableTaskGraphDigest,
        List<String> executableTaskIds,
        String status) {

    /** The graph was loaded and re-derived, but execution was not requested. */
    public static final String STATUS_PREPARED = "PREPARED";

    /** The whole graph executed through the closed loop and its outputs were committed. */
    public static final String STATUS_EXECUTED = "EXECUTED";

    /** A failure was recorded for this run instead of propagating (see workflow status rules). */
    public static final String STATUS_FAILED = "FAILED";

    public MediaTaskWorkflowResult {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(planDigest, "planDigest");
        Objects.requireNonNull(expectedExecutableTaskGraphDigest, "expectedExecutableTaskGraphDigest");
        Objects.requireNonNull(status, "status");
        executableTaskIds = executableTaskIds == null ? List.of() : List.copyOf(executableTaskIds);
    }
}
