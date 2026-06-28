package com.example.platform.render.domain.template;

import java.util.Map;

/**
 * Provider-neutral capability requirement for a template or operation.
 * Internal domain model. Does not bind to provider names or execution environments.
 *
 * <p>Common capabilities: TEXT_OVERLAY, IMAGE_OVERLAY, AUDIO_MIX,
 * VIDEO_COMPOSITE, TRANSITION, SUBTITLE_BURN_IN, LAYOUT_COMPOSITION.</p>
 */
public record TemplateCapabilityRequirement(
        String capability,
        boolean required,
        Map<String, String> constraints) {

    public TemplateCapabilityRequirement {
        if (capability == null || capability.isBlank())
            throw new IllegalArgumentException("Capability must not be blank");
    }

    public static TemplateCapabilityRequirement required(String capability) {
        return new TemplateCapabilityRequirement(capability, true, Map.of());
    }
}
