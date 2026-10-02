package com.example.platform.workerfabric.reuse;

/** Fail-closed typed error: a runtime execution cannot be constructed for this task/grant. */
public final class TaskRuntimeExecutionConstructionException extends IllegalStateException {

    public TaskRuntimeExecutionConstructionException(String code, String detail) {
        super(code + ": " + detail);
    }
}
