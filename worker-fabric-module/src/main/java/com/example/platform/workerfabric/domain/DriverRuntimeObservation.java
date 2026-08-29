package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** Exact driver/runtime API evidence observed for one device. */
public record DriverRuntimeObservation(
        RuntimeDependencyVersion version,
        Optional<RuntimeDependencyAbi> abi)
        implements Serializable {

    public DriverRuntimeObservation {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(abi, "abi");
    }
}
