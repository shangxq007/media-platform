package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact dependency content reported by a bounded provider-runtime probe. */
public record RuntimeDependencyObservedDependency(
        RuntimeDependencyCoordinate coordinate,
        RuntimeDependencyVersion version,
        Optional<RuntimeDependencyAbi> abi,
        List<String> enabledFeatures,
        List<String> enabledBuildRuntimeFlags)
        implements Comparable<RuntimeDependencyObservedDependency>, Serializable {

    public RuntimeDependencyObservedDependency {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(abi, "abi");
        enabledFeatures = RuntimeDependencyNames.canonicalize(enabledFeatures, "enabledFeatures");
        enabledBuildRuntimeFlags = RuntimeDependencyNames.canonicalize(
                enabledBuildRuntimeFlags, "enabledBuildRuntimeFlags");
    }

    @Override
    public int compareTo(RuntimeDependencyObservedDependency other) {
        return coordinate.compareTo(other.coordinate);
    }
}
