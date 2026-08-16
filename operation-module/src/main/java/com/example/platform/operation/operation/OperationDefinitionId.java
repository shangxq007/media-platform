package com.example.platform.operation.operation;

import java.util.Objects;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM6): typed stable namespaced identity of an
 * OperationDefinition. Namespaced semantic id (e.g. "timeline.move");
 * NEVER Java class name / controller method / UI label / provider identity.
 */
public record OperationDefinitionId(String value) implements Comparable<OperationDefinitionId> {

    public OperationDefinitionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("OperationDefinitionId must not be blank");
        }
    }

    public static OperationDefinitionId of(String value) {
        return new OperationDefinitionId(value);
    }

    @Override
    public int compareTo(OperationDefinitionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
