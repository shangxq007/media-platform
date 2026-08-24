package com.example.platform.workerfabric.domain;

/** Backend-neutral A3 execution-attempt lifecycle. */
public enum ExecutionAttemptState {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    ABANDONED;

    public boolean terminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELLED
                || this == ABANDONED;
    }

    public boolean canTransitionTo(ExecutionAttemptState next) {
        if (next == null || terminal() || next == this) {
            return false;
        }
        return switch (this) {
            case CREATED -> next == RUNNING
                    || next == FAILED
                    || next == CANCELLED
                    || next == ABANDONED;
            case RUNNING -> next.terminal();
            case SUCCEEDED, FAILED, CANCELLED, ABANDONED -> false;
        };
    }
}
