package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityStability;

/**
 * Descriptor for a domain event type.
 *
 * <p>EventTypeDescriptor defines the shape and metadata for a domain event type.
 * It is used for event type registration and discovery.</p>
 *
 * <p><strong>Contract only:</strong> This defines the event type descriptor shape.
 * Event bus/runtime is not implemented.</p>
 */
public record EventTypeDescriptor(
    String eventType,
    String eventVersion,
    String sourceModule,
    String payloadSchemaRef,
    CapabilityStability stability,
    String description
) {
    public EventTypeDescriptor {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion == null || eventVersion.isBlank()) {
            throw new IllegalArgumentException("eventVersion must not be blank");
        }
    }
}
