package com.example.platform.execution.domain.provider;

import java.util.Objects;

/** Version of behavior that can affect provider lowering or executable binding. */
public record ProviderVersion(String value) {

    public ProviderVersion {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("provider version must be a non-blank canonical value");
        }
    }

    public static ProviderVersion of(String value) {
        return new ProviderVersion(value);
    }
}
