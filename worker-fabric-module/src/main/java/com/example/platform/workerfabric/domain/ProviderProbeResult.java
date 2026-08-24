package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Objects;

/** Mutable runtime probe evidence, already freshness-classified by runtime policy. */
public record ProviderProbeResult(ProviderBindingPin providerBindingPin, Status status) {

    public ProviderProbeResult {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(status, "status");
    }

    public enum Status {
        HEALTHY,
        UNKNOWN,
        STALE,
        FAILED
    }
}
