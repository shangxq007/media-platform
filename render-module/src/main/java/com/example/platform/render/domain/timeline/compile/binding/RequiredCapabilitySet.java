package com.example.platform.render.domain.timeline.compile.binding;

import java.util.List;

/**
 * A set of required capabilities for provider binding.
 *
 * <p>Encapsulates the capability requirements from a LogicalCapabilityNode
 * in a form suitable for provider matching.</p>
 *
 * @param capabilities  list of required capability codes
 * @param fidelityLevel desired fidelity: DRAFT, PREVIEW, PRODUCTION
 * @param isOptional    whether the capability is optional (can be skipped)
 */
public record RequiredCapabilitySet(
        List<String> capabilities,
        String fidelityLevel,
        boolean isOptional) {

    /**
     * Creates a required capability set with PRODUCTION fidelity.
     */
    public static RequiredCapabilitySet production(List<String> capabilities) {
        return new RequiredCapabilitySet(capabilities, "PRODUCTION", false);
    }

    /**
     * Creates a required capability set with PREVIEW fidelity.
     */
    public static RequiredCapabilitySet preview(List<String> capabilities) {
        return new RequiredCapabilitySet(capabilities, "PREVIEW", false);
    }

    /**
     * Creates an optional capability set that can be skipped.
     */
    public static RequiredCapabilitySet optional(List<String> capabilities) {
        return new RequiredCapabilitySet(capabilities, "PRODUCTION", true);
    }

    /**
     * Returns true if this set contains the given capability.
     */
    public boolean contains(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Returns true if this set is empty.
     */
    public boolean isEmpty() {
        return capabilities == null || capabilities.isEmpty();
    }
}
