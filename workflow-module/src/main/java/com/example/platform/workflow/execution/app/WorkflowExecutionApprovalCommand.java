package com.example.platform.workflow.execution.app;

/**
 * Frozen approval/rejection command (typed signal payload for APPROVAL nodes).
 */
public record WorkflowExecutionApprovalCommand(
        String tenantId,
        String executionId,
        boolean approved,
        String approverActorId,
        String comment) {

    public WorkflowExecutionApprovalCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (approverActorId == null || approverActorId.isBlank()) {
            throw new IllegalArgumentException("approverActorId must not be blank");
        }
    }
}
