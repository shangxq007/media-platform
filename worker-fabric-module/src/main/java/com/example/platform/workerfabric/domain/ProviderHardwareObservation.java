package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Freshness-bound evidence tied to exact provider, runtime, and physical host identities. */
public record ProviderHardwareObservation(
        ProviderImplementationId providerImplementationId,
        WorkerRuntimeId workerRuntimeId,
        PhysicalHostId physicalHostId,
        Optional<DeviceId> deviceId,
        Instant observedAt,
        Instant expiresAt,
        ProviderHardwareProbeEvidence evidence)
        implements Serializable {

    public ProviderHardwareObservation {
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(evidence, "evidence");
        if (!observedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("provider hardware observation must expire after observation");
        }
        if (evidence instanceof ProviderHardwareAvailableEvidence available
                && available.deviceEvidence().isPresent()
                && deviceId.isEmpty()) {
            throw new IllegalArgumentException("device evidence requires an exact device binding");
        }
    }

    /** Freshness is inclusive at observation and exclusive at expiry. */
    public boolean isFreshAt(Instant assessedAt) {
        Objects.requireNonNull(assessedAt, "assessedAt");
        return !assessedAt.isBefore(observedAt) && assessedAt.isBefore(expiresAt);
    }
}
