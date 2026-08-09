package com.example.platform.workflow.execution.port;

import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionId;

import java.util.Optional;

/**
 * Workflow execution persistence port (product/query/audit authority).
 */
public interface WorkflowExecutionRepository {

    void insert(WorkflowExecution execution);

    void updateStatus(WorkflowExecution execution);

    Optional<WorkflowExecution> findById(WorkflowExecutionId id);

    Optional<WorkflowExecution> findByIdempotencyKey(String tenantId, String idempotencyKey);
}
