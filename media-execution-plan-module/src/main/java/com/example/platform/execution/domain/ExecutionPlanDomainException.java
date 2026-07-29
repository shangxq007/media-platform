package com.example.platform.execution.domain;

/**
 * Exception type for Execution Plan domain errors.
 */
public class ExecutionPlanDomainException extends RuntimeException {
    private final ExecutionPlanErrorCode.Error error;

    public ExecutionPlanDomainException(ExecutionPlanErrorCode.Error error) {
        super(error.code().title() + " [" + error.code().codeString() + "]");
        this.error = error;
    }

    public ExecutionPlanErrorCode.Error error() { return error; }
    public ExecutionPlanErrorCode code() { return error.code(); }
}
