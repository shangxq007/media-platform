package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Typed capability requirement on a render node (C17). The capability id is drawn
 * from the bounded {@link RenderCapabilityId} vocabulary.
 */
public record RenderCapabilityRequirement(RenderCapabilityId capabilityId) {

    public RenderCapabilityRequirement {
        Objects.requireNonNull(capabilityId, "capabilityId");
    }
}
