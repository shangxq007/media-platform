package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Objects;

/** Exact server-owned provider/runtime support requirement for one provider-bound task. */
public record WorkerRuntimeSupportRequirement(
        ProviderBindingPin providerBindingPin,
        RuntimeLifecycleKind requiredRuntimeKind,
        RuntimeSupportIdentifier supportIdentifier) {

    public WorkerRuntimeSupportRequirement {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(requiredRuntimeKind, "requiredRuntimeKind");
        Objects.requireNonNull(supportIdentifier, "supportIdentifier");
    }
}
