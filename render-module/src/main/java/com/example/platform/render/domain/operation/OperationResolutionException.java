package com.example.platform.render.domain.operation;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM17): typed Operation resolution exception.
 * Stable semantic error code, never arbitrary message strings as API.
 */
public class OperationResolutionException extends RuntimeException {

    private final OperationErrorCode code;

    public OperationResolutionException(OperationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public OperationErrorCode code() {
        return code;
    }
}
