package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded provider execution-mechanics support projection.
 *
 * <p>This is not a capability profile, provider registry, or optimizer. Static capability and
 * provider legality remain owned by the Stage-1 compatibility graph.
 */
public record ProviderBackendExecutionSupport(
        ProviderBindingPin providerBindingPin,
        Knowledge knowledge,
        Set<ExecutionBackend> supportedBackends) {

    public ProviderBackendExecutionSupport {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(supportedBackends, "supportedBackends");
        EnumSet<ExecutionBackend> canonical = supportedBackends.isEmpty()
                ? EnumSet.noneOf(ExecutionBackend.class)
                : EnumSet.copyOf(supportedBackends);
        supportedBackends = Collections.unmodifiableSet(canonical);
        if (knowledge == Knowledge.UNKNOWN && !supportedBackends.isEmpty()) {
            throw new IllegalArgumentException("unknown backend support cannot claim supported backends");
        }
    }

    public static ProviderBackendExecutionSupport declared(
            ProviderBindingPin providerBindingPin,
            Set<ExecutionBackend> supportedBackends) {
        return new ProviderBackendExecutionSupport(
                providerBindingPin, Knowledge.DECLARED, supportedBackends);
    }

    public static ProviderBackendExecutionSupport unknown(ProviderBindingPin providerBindingPin) {
        return new ProviderBackendExecutionSupport(
                providerBindingPin, Knowledge.UNKNOWN, Set.of());
    }

    public enum Knowledge {
        DECLARED,
        UNKNOWN
    }
}
