package com.example.platform.workflow.execution.domain;

/**
 * Execution trigger vocabulary (UWEV1-ARSF). Scheduler is TRIGGER ONLY.
 */
public enum WorkflowExecutionTrigger {
    MANUAL,
    SCHEDULED,
    WEBHOOK,
    API,
    SYSTEM
}
