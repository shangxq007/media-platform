package com.example.platform.render.domain.renderplan;

import java.util.Objects;
import java.util.Set;

/**
 * Available capability set consumed by planning (C17). Typed; NEVER a fingerprint
 * input — capability context affects plan status/diagnostics but never the
 * deterministic plan fingerprint (C7).
 *
 * @param availableCapabilities the set of capabilities available in this planning context
 */
public record CapabilityContext(Set<RenderCapabilityId> availableCapabilities) {

    public CapabilityContext {
        Objects.requireNonNull(availableCapabilities, "availableCapabilities");
        availableCapabilities = Set.copyOf(availableCapabilities);
    }

    public static CapabilityContext none() {
        return new CapabilityContext(Set.of());
    }

    public boolean supports(RenderCapabilityId capabilityId) {
        return availableCapabilities.contains(capabilityId);
    }
}
