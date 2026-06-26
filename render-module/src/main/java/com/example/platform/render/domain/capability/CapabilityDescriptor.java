package com.example.platform.render.domain.capability;

import java.util.List;

/**
 * Capability descriptor — immutable metadata about a Producer's capability.
 * Used by the Capability Catalog for planning. No execution logic.
 */
public record CapabilityDescriptor(
        String capabilityId,
        String capability,
        String producerId,
        String producerName,
        String producerVersion,
        String backendId,
        String backendType,
        List<String> supportedRepresentations,
        List<String> producedProductTypes,
        boolean preferred,
        int priority,
        boolean enabled) {

    public static CapabilityDescriptor of(String capability, String producerId,
                                            String backendId, String backendType) {
        return new CapabilityDescriptor(capability + ":" + producerId, capability,
                producerId, producerId, "1.0", backendId, backendType,
                List.of("JSON_DOCUMENT"), List.of("TRANSCRIPT"),
                false, 50, true);
    }
}
