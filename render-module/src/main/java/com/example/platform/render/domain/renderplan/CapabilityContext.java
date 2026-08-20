package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import java.util.Objects;
import java.util.Set;

/**
 * Available capability set consumed by planning (C17, correction F3). Uses the
 * platform capability authority ({@link CapabilityId}); typed; NEVER a
 * fingerprint input — capability context affects plan status/diagnostics but
 * never the deterministic plan fingerprint (C7).
 *
 * @param availableCapabilities the set of platform capabilities available in this planning context
 */
public record CapabilityContext(Set<CapabilityId> availableCapabilities) {

    public CapabilityContext {
        Objects.requireNonNull(availableCapabilities, "availableCapabilities");
        availableCapabilities = Set.copyOf(availableCapabilities);
    }

    public static CapabilityContext none() {
        return new CapabilityContext(Set.of());
    }

    public boolean supports(CapabilityId capabilityId) {
        return availableCapabilities.contains(capabilityId);
    }
}
