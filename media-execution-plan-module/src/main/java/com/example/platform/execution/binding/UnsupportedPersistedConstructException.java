package com.example.platform.execution.binding;

import java.util.Objects;

/**
 * Fail-closed carrier for a persisted construct outside the bounded V1
 * serialization surface.
 *
 * <p>The durable form must never silently drop or reinterpret semantics, so any
 * value the bounded codec cannot represent faithfully is rejected here instead.
 */
public class UnsupportedPersistedConstructException extends RuntimeException {

    private final String construct;

    public UnsupportedPersistedConstructException(String construct) {
        super("unsupported persisted construct: " + construct);
        this.construct = Objects.requireNonNull(construct, "construct");
    }

    public String construct() {
        return construct;
    }
}
