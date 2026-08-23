package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of one executable runtime endpoint. */
public record WorkerRuntimeId(String value) implements Serializable {

    public WorkerRuntimeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WorkerRuntimeId must not be blank");
        }
    }

    public static WorkerRuntimeId of(String value) {
        return new WorkerRuntimeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
