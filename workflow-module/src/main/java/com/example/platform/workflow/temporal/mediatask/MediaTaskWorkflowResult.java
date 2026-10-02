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

    public MediaTaskWorkflowResult {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(planDigest, "planDigest");
        Objects.requireNonNull(expectedExecutableTaskGraphDigest, "expectedExecutableTaskGraphDigest");
        Objects.requireNonNull(status, "status");
        executableTaskIds = executableTaskIds == null ? List.of() : List.copyOf(executableTaskIds);
    }
}
