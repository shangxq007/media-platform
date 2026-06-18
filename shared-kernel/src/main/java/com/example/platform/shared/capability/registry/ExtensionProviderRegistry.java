package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.ExtensionProvider;

import java.util.List;
import java.util.Optional;

/**
 * Registry for extension providers.
 *
 * <p><strong>Contract only:</strong> This defines the registry interface.
 * Runtime execution is not implemented.</p>
 */
public interface ExtensionProviderRegistry {

    /**
     * Register an extension provider.
     *
     * @param provider the provider to register
     * @throws IllegalArgumentException if provider is null
     * @throws CapabilityRegistryException if provider id is duplicate
     */
    void register(ExtensionProvider provider);

    /**
     * Find a provider by id.
     *
     * @param providerId the provider id
     * @return the provider, or empty if not found
     */
    Optional<ExtensionProvider> findByProviderId(String providerId);

    /**
     * List all registered providers.
     *
     * @return immutable list of all providers
     */
    List<ExtensionProvider> list();

    /**
     * List providers supporting a specific extension point.
     *
     * @param extensionPointKey the extension point key
     * @return immutable list of providers supporting the extension point
     */
    List<ExtensionProvider> findSupporting(String extensionPointKey);

    /**
     * Check if a provider is registered.
     *
     * @param providerId the provider id
     * @return true if registered
     */
    boolean contains(String providerId);
}
