package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.hook.HookPhase;
import com.example.platform.shared.capability.hook.HookPoint;

import java.util.List;
import java.util.Optional;

/**
 * Registry for hook points.
 *
 * <p><strong>Contract only:</strong> This defines the registry interface.
 * Hook runtime is not implemented.</p>
 */
public interface HookPointRegistry {

    /**
     * Register a hook point.
     *
     * @param hookPoint the hook point to register
     * @throws IllegalArgumentException if hookPoint is null
     * @throws CapabilityRegistryException if key/phase is duplicate
     */
    void register(HookPoint hookPoint);

    /**
     * Find a hook point by key and phase.
     *
     * @param key the hook point key
     * @param phase the hook phase
     * @return the hook point, or empty if not found
     */
    Optional<HookPoint> find(String key, HookPhase phase);

    /**
     * List all registered hook points.
     *
     * @return immutable list of all hook points
     */
    List<HookPoint> list();

    /**
     * Check if a hook point is registered.
     *
     * @param key the hook point key
     * @param phase the hook phase
     * @return true if registered
     */
    boolean contains(String key, HookPhase phase);
}
