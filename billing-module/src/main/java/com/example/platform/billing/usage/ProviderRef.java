package com.example.platform.billing.usage;

import java.util.Objects;

/**
 * Provider reference reusing existing provider identifiers (Plugin Capability Registry /
 * existing provider ids). No second provider registry is introduced.
 *
 * @param providerId the provider identifier (required)
 */
public record ProviderRef(String providerId) {

    public ProviderRef {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
    }
}
