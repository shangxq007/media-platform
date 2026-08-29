package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** Implementation-local driver/runtime API constraint; never an installation request. */
public record DriverRuntimeRequirement(
        RuntimeDependencyVersionConstraint versionConstraint,
        Optional<RuntimeDependencyAbi> abiConstraint)
        implements Serializable {

    public DriverRuntimeRequirement {
        Objects.requireNonNull(versionConstraint, "versionConstraint");
        Objects.requireNonNull(abiConstraint, "abiConstraint");
    }
}
