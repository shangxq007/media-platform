package com.example.platform.shared.usage;

/** Stable reference to the provider implementation that performed the operation. */
public record ProviderRef(String providerId) {

    public ProviderRef {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be null/blank");
        }
    }
}
