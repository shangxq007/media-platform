package com.example.platform.execution.domain.provider;

import java.util.Objects;

/**
 * Stable identity of one provider runtime/adapter implementation; never a worker,
 * device, installation instance, capability id, or capability implementation id.
 */
public record ProviderImplementationId(String value) {

    public ProviderImplementationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("provider implementation id must be a non-blank canonical value");
        }
    }

    public static ProviderImplementationId of(String value) {
        return new ProviderImplementationId(value);
    }
}
