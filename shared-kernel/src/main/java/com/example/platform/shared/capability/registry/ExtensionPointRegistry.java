package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.ExtensionPoint;

import java.util.List;
import java.util.Optional;

/**
 * Registry for extension points.
 *
 * <p><strong>Contract only:</strong> This defines the registry interface.
 * Runtime execution is not implemented.</p>
 */
public interface ExtensionPointRegistry {

    /**
     * Register an extension point.
     *
     * @param extensionPoint the extension point to register
     * @throws IllegalArgumentException if extensionPoint is null
     * @throws CapabilityRegistryException if key/version is duplicate
     */
    void register(ExtensionPoint extensionPoint);

    /**
     * Find an extension point by key and version.
     *
     * @param key the extension point key
     * @param version the version
     * @return the extension point, or empty if not found
     */
    Optional<ExtensionPoint> find(String key, String version);

    /**
     * List all registered extension points.
     *
     * @return immutable list of all extension points
     */
    List<ExtensionPoint> list();

    /**
     * Check if an extension point is registered.
     *
     * @param key the extension point key
     * @param version the version
     * @return true if registered
     */
    boolean contains(String key, String version);
}
