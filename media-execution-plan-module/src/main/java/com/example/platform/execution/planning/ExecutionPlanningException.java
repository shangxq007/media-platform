package com.example.platform.execution.planning;

import java.util.Objects;

/**
 * Roadmap #21 typed planning failure carrier (C19).
 *
 * <p>Typed failure reason + machine-readable context. NO free-text semantic
 * branching. Distinct from #22 runtime failure policy.
 */
public class ExecutionPlanningException extends RuntimeException {

    private final ExecutionPlanningFailureReason reason;
    private final String context;

    public ExecutionPlanningException(ExecutionPlanningFailureReason reason, String context) {
        super(reason.name() + (context != null ? ": " + context : ""));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.context = context;
    }

    public ExecutionPlanningFailureReason reason() {
        return reason;
    }

    public String context() {
        return context;
    }
}
