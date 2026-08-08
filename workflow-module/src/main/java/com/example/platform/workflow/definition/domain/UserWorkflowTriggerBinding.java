package com.example.platform.workflow.definition.domain;

/**
 * Trigger binding declaration. A declaration only: no scheduler registration,
 * no execution semantics (execution-binding-contract.txt).
 */
public record UserWorkflowTriggerBinding(
        TriggerType triggerType,
        String referenceId,
        String referenceVersion) {

    public UserWorkflowTriggerBinding {
        if (triggerType == null) {
            throw new IllegalArgumentException("trigger type must not be null");
        }
        if (triggerType != TriggerType.MANUAL && (referenceId == null || referenceId.isBlank())) {
            throw new IllegalArgumentException("scheduled/event trigger requires a reference id");
        }
    }

    public enum TriggerType {
        MANUAL,
        SCHEDULE_REF,
        EVENT_REF
    }

    public static UserWorkflowTriggerBinding manual() {
        return new UserWorkflowTriggerBinding(TriggerType.MANUAL, null, null);
    }
}
