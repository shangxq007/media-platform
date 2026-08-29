package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Version of the bounded runtime dependency observation schema. */
public record RuntimeDependencyProbeSchemaVersion(int value) implements Serializable {

    public static final RuntimeDependencyProbeSchemaVersion CURRENT =
            new RuntimeDependencyProbeSchemaVersion(1);

    public RuntimeDependencyProbeSchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("runtime dependency probe schema version must be positive");
        }
    }
}
