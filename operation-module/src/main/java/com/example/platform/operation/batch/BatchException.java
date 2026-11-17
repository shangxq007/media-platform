package com.example.platform.operation.batch;

/** Typed batch rejection; {@code stepIndex} is present when a specific step caused it. */
@org.springframework.modulith.NamedInterface("batch")
public final class BatchException extends RuntimeException {

    private final BatchErrorCode code;
    private final Integer stepIndex;

    public BatchException(BatchErrorCode code, Integer stepIndex, String message) {
        this(code, stepIndex, message, null);
    }

    public BatchException(BatchErrorCode code, Integer stepIndex, String message, Throwable cause) {
        super(message, cause);
        if (code == null) {
            throw new IllegalArgumentException("batch error code required");
        }
        this.code = code;
        this.stepIndex = stepIndex;
    }

    public BatchErrorCode code() {
        return code;
    }

    public Integer stepIndex() {
        return stepIndex;
    }

    public BatchException withStep(int index) {
        return stepIndex == null ? new BatchException(code, index, getMessage(), this) : this;
    }
}
