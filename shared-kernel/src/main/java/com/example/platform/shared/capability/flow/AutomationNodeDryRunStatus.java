package com.example.platform.shared.capability.flow;

/**
 * Status of a single node in an automation flow dry-run execution.
 */
public enum AutomationNodeDryRunStatus {
    DRY_RUN_SUCCEEDED,
    VALIDATION_FAILED,
    NOT_IMPLEMENTED,
    SKIPPED,
    FAILED
}
