package com.example.platform.execution.domain.provider;

import java.util.Objects;

/** Stable identity of a provider/backend family; never a capability identity. */
public record ProviderId(String value) {

    public ProviderId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("provider id must be a non-blank canonical value");
        }
    }

    public static ProviderId of(String value) {
        return new ProviderId(value);
    }
}
