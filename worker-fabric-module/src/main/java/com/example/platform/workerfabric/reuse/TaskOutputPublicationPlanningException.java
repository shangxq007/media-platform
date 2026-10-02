package com.example.platform.workerfabric.reuse;

/** Fail-closed typed error: a task output publication intent cannot be planned. */
public final class TaskOutputPublicationPlanningException extends IllegalStateException {

    public TaskOutputPublicationPlanningException(String code, String detail) {
        super(code + ": " + detail);
    }
}
