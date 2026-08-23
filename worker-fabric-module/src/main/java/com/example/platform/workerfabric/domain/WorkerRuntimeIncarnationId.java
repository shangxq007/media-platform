package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Identity of one registration incarnation of a worker runtime. */
public record WorkerRuntimeIncarnationId(String value) implements Serializable {

    public WorkerRuntimeIncarnationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WorkerRuntimeIncarnationId must not be blank");
        }
    }

    public static WorkerRuntimeIncarnationId of(String value) {
        return new WorkerRuntimeIncarnationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
