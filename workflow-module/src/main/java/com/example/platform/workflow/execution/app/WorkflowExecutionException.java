package com.example.platform.workflow.execution.app;

/**
 * Canonical execution error (UWEV1-ARSF failure model — not just FAILED+message).
 */
public class WorkflowExecutionException extends RuntimeException {

    public enum Code {
        DEFINITION_NOT_FOUND,
        DEFINITION_VERSION_NOT_FOUND,
        DEFINITION_NOT_PUBLISHED,
        EXECUTION_NOT_FOUND,
        EXECUTION_NOT_WAITING,
        EXECUTION_ALREADY_TERMINAL
    }

    private final Code code;

    public WorkflowExecutionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}

final class WorkflowExecutionErrorCode {
    static final String DEFINITION_NOT_FOUND = "DEFINITION_NOT_FOUND";
    static final String DEFINITION_VERSION_NOT_FOUND = "DEFINITION_VERSION_NOT_FOUND";
    static final String DEFINITION_NOT_PUBLISHED = "DEFINITION_NOT_PUBLISHED";
    static final String EXECUTION_NOT_FOUND = "EXECUTION_NOT_FOUND";
}
