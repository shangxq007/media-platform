package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable dependency requirement owned by one exact provider implementation. */
public record RuntimeDependencyRequirement(
        ProviderImplementationId providerImplementationId,
        RuntimeDependencyCoordinate coordinate,
        RuntimeDependencyVersionConstraint versionConstraint,
        Optional<RuntimeDependencyAbi> abiConstraint,
        List<String> requiredFeatures,
        List<String> requiredBuildRuntimeFlags)
        implements Serializable {

    public RuntimeDependencyRequirement {
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(versionConstraint, "versionConstraint");
        Objects.requireNonNull(abiConstraint, "abiConstraint");
        requiredFeatures = RuntimeDependencyNames.canonicalize(requiredFeatures, "requiredFeatures");
        requiredBuildRuntimeFlags = RuntimeDependencyNames.canonicalize(
                requiredBuildRuntimeFlags, "requiredBuildRuntimeFlags");
    }
}
