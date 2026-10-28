package com.example.platform.execution.admission;

/** Existing platform_execution_admission lifecycle, shared by all input domains. */
public enum PlatformExecutionState {
    ADMITTED, RUNNING, RETRYING, COMPLETED, FAILED, CANCELLED;
    public boolean canTransitionTo(PlatformExecutionState next) {
        if (next == this) return true;
        return switch(this) {
            case ADMITTED, RETRYING -> next == RUNNING || next == FAILED || next == CANCELLED;
            case RUNNING -> next == COMPLETED || next == FAILED || next == CANCELLED;
            case FAILED -> next == RETRYING;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
