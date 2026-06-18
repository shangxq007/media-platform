package com.example.platform.shared.capability.registry;

import java.util.List;
import java.util.Optional;

/**
 * Registry for event types.
 *
 * <p><strong>Contract only:</strong> This defines the registry interface.
 * Event bus/runtime is not implemented.</p>
 */
public interface EventTypeRegistry {

    /**
     * Register an event type descriptor.
     *
     * @param descriptor the event type descriptor to register
     * @throws IllegalArgumentException if descriptor is null
     * @throws CapabilityRegistryException if eventType/version is duplicate
     */
    void register(EventTypeDescriptor descriptor);

    /**
     * Find an event type by type and version.
     *
     * @param eventType the event type
     * @param eventVersion the version
     * @return the descriptor, or empty if not found
     */
    Optional<EventTypeDescriptor> find(String eventType, String eventVersion);

    /**
     * List all registered event types.
     *
     * @return immutable list of all event type descriptors
     */
    List<EventTypeDescriptor> list();

    /**
     * Check if an event type is registered.
     *
     * @param eventType the event type
     * @param eventVersion the version
     * @return true if registered
     */
    boolean contains(String eventType, String eventVersion);
}
