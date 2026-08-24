package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Objects;

/** Typed remote-provider backend identity and its integration interaction mechanic. */
public record RemoteProviderBackend(
        ProviderBindingPin providerBindingPin,
        RemoteProviderInteractionMode interactionMode) {

    public RemoteProviderBackend {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(interactionMode, "interactionMode");
    }

    public ExecutionBackend executionBackend() {
        return ExecutionBackend.REMOTE_PROVIDER;
    }

    public PlacementAuthorityScope placementAuthorityScope() {
        return PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED;
    }
}
