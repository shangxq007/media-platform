package com.example.platform.operation.plan;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1: typed plan/apply exception.
 */
public class PlanException extends RuntimeException {

    private final PlanErrorCode code;

    public PlanException(PlanErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public PlanErrorCode code() {
        return code;
    }
}
