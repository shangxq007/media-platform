package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.SystemAction;

import java.util.List;
import java.util.Optional;

/**
 * Registry for system actions.
 *
 * <p><strong>Contract only:</strong> This defines the registry interface.
 * Runtime execution is not implemented.</p>
 */
public interface SystemActionRegistry {

    /**
     * Register a system action.
     *
     * @param action the action to register
     * @throws IllegalArgumentException if action is null
     * @throws CapabilityRegistryException if action key is duplicate
     */
    void register(SystemAction action);

    /**
     * Find a system action by key.
     *
     * @param actionKey the action key
     * @return the action, or empty if not found
     */
    Optional<SystemAction> findByKey(String actionKey);

    /**
     * List all registered system actions.
     *
     * @return immutable list of all actions
     */
    List<SystemAction> list();

    /**
     * Check if a system action is registered.
     *
     * @param actionKey the action key
     * @return true if registered
     */
    boolean contains(String actionKey);
}
