package com.example.platform.compositeresource.domain;

public record CompositeComponentId(String value) implements Comparable<CompositeComponentId> {
    public CompositeComponentId {
        value = OpaqueIdentityPolicy.requireComponent(value);
    }

    @Override
    public int compareTo(CompositeComponentId other) {
        return value.compareTo(other.value);
    }
}
