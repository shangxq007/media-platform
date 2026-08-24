package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Objects;

/** Typed identity of the bounded OpenCue farm backend for one immutable provider binding. */
public record OpenCueFarmBackend(ProviderBindingPin providerBindingPin) {

    public OpenCueFarmBackend {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
    }

    public ExecutionBackend executionBackend() {
        return ExecutionBackend.OPEN_CUE_FARM;
    }

    public PlacementAuthorityScope placementAuthorityScope() {
        return PlacementAuthorityScope.BACKEND_DELEGATED;
    }
}
