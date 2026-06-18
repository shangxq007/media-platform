package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;

import java.util.*;

/**
 * In-memory implementation of EventTypeRegistry.
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Event bus/runtime is not implemented.</p>
 */
public class InMemoryEventTypeRegistry implements EventTypeRegistry {

    private final Map<String, EventTypeDescriptor> eventTypes = new LinkedHashMap<>();

    @Override
    public void register(EventTypeDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("EventTypeDescriptor must not be null");
        }

        String eventType = descriptor.eventType();
        String eventVersion = descriptor.eventVersion();

        if (eventType == null || eventType.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "Event type must not be blank"
            );
        }

        if (eventVersion == null || eventVersion.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "Event version must not be blank"
            );
        }

        String compositeKey = eventType + ":" + eventVersion;
        if (eventTypes.containsKey(compositeKey)) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.CONFLICT,
                "Event type already registered: " + compositeKey
            );
        }

        eventTypes.put(compositeKey, descriptor);
    }

    @Override
    public Optional<EventTypeDescriptor> find(String eventType, String eventVersion) {
        if (eventType == null || eventType.isBlank() || eventVersion == null || eventVersion.isBlank()) {
            return Optional.empty();
        }
        String compositeKey = eventType + ":" + eventVersion;
        return Optional.ofNullable(eventTypes.get(compositeKey));
    }

    @Override
    public List<EventTypeDescriptor> list() {
        return Collections.unmodifiableList(new ArrayList<>(eventTypes.values()));
    }

    @Override
    public boolean contains(String eventType, String eventVersion) {
        if (eventType == null || eventType.isBlank() || eventVersion == null || eventVersion.isBlank()) {
            return false;
        }
        String compositeKey = eventType + ":" + eventVersion;
        return eventTypes.containsKey(compositeKey);
    }
}
