package com.example.platform.workflow.execution.domain;

import java.util.Objects;

/**
 * Canonical User Workflow Execution identity (UWE-ADR-002).
 *
 * <p>DISTINCT from {@code UserWorkflowDefinitionId}: one definition can execute
 * many times. Tenant-scoped, stable, collision-safe. The Temporal workflow id is
 * deterministically derived from this id (UWE-ADR-005) — never from the
 * definition id.</p>
 */
public record WorkflowExecutionId(String executionId, String tenantId) {

    public WorkflowExecutionId {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    /**
     * Deterministic Temporal workflow id (UWE-ADR-005): tenant-scoped prefix +
     * execution id. Audit-friendly, collision-safe across tenants.
     */
    public String temporalWorkflowId() {
        return "uwe-" + tenantId + "-" + executionId;
    }
}
