package com.example.platform.workflow.execution.app;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.workflow.execution.domain.WorkflowExecutionTrigger;

import java.util.Objects;

/**
 * Frozen start contract (UWEV1-ARSF). Bounded typed input references only —
 * never SecurityContext / JWT / provider SDK objects / secret values / raw media.
 */
public record StartWorkflowExecutionCommand(
        String tenantId,
        CanonicalActorRef actor,
        String definitionId,
        Integer definitionVersion,   // optional: null = latest PUBLISHED
        WorkflowExecutionTrigger trigger,
        String inputRefsJson,        // JSON array of ArtifactRef / stable IDs
        String idempotencyKey) {

    public StartWorkflowExecutionCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(actor, "actor");
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("definitionId must not be blank");
        }
        Objects.requireNonNull(trigger, "trigger");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
