package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, freshness-bound evidence for one exact provider/runtime/device probe. */
public record RuntimeDependencyObservation(
        ProviderImplementationId providerImplementationId,
        WorkerRuntimeId workerRuntimeId,
        Optional<DeviceId> deviceId,
        RuntimeDependencyProbeSchemaVersion probeSchemaVersion,
        Instant observedAt,
        Instant expiresAt,
        List<RuntimeDependencyObservedDependency> dependencies)
        implements Serializable {

    public RuntimeDependencyObservation {
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(probeSchemaVersion, "probeSchemaVersion");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(dependencies, "dependencies");
        if (!observedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("runtime dependency observation must expire after it was observed");
        }
        ArrayList<RuntimeDependencyObservedDependency> canonical = new ArrayList<>(dependencies.size());
        HashSet<RuntimeDependencyCoordinate> coordinates = new HashSet<>();
        for (RuntimeDependencyObservedDependency dependency : dependencies) {
            Objects.requireNonNull(dependency, "dependencies entry");
            if (!coordinates.add(dependency.coordinate())) {
                throw new IllegalArgumentException("runtime dependency observation has duplicate coordinates");
            }
            canonical.add(dependency);
        }
        canonical.sort(RuntimeDependencyObservedDependency::compareTo);
        dependencies = List.copyOf(canonical);
    }

    /** Freshness interval is inclusive at observation and exclusive at expiry. */
    public boolean isFreshAt(Instant assessedAt) {
        Objects.requireNonNull(assessedAt, "assessedAt");
        return !assessedAt.isBefore(observedAt) && assessedAt.isBefore(expiresAt);
    }
}
