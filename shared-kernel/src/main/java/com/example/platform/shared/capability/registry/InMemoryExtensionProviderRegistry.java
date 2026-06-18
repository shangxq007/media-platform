package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;
import com.example.platform.shared.capability.ExtensionProvider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ExtensionProviderRegistry.
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Runtime execution is not implemented.</p>
 */
public class InMemoryExtensionProviderRegistry implements ExtensionProviderRegistry {

    private final Map<String, ExtensionProvider> providers = new LinkedHashMap<>();

    @Override
    public void register(ExtensionProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ExtensionProvider must not be null");
        }

        String providerId = provider.providerId();
        if (providerId == null || providerId.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "ExtensionProvider id must not be blank"
            );
        }

        if (provider.supportedExtensionPoints() == null || provider.supportedExtensionPoints().isEmpty()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "ExtensionProvider must support at least one extension point"
            );
        }

        if (providers.containsKey(providerId)) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.CONFLICT,
                "ExtensionProvider already registered: " + providerId
            );
        }

        providers.put(providerId, provider);
    }

    @Override
    public Optional<ExtensionProvider> findByProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerId));
    }

    @Override
    public List<ExtensionProvider> list() {
        return Collections.unmodifiableList(new ArrayList<>(providers.values()));
    }

    @Override
    public List<ExtensionProvider> findSupporting(String extensionPointKey) {
        if (extensionPointKey == null || extensionPointKey.isBlank()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
            providers.values().stream()
                .filter(p -> p.supportedExtensionPoints() != null && p.supportedExtensionPoints().contains(extensionPointKey))
                .collect(Collectors.toList())
        );
    }

    @Override
    public boolean contains(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return false;
        }
        return providers.containsKey(providerId);
    }
}
