package com.example.platform.render.domain.timeline.compile.binding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Deterministic identifier for a ProviderBindingPlan.
 *
 * <p>Derived from the source capability graph ID and binding decisions
 * to ensure reproducibility.</p>
 */
public record ProviderBindingPlanId(String value) {

    /**
     * Creates a deterministic plan ID from a capability graph ID.
     */
    public static ProviderBindingPlanId fromCapabilityGraphId(String capabilityGraphId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(capabilityGraphId.getBytes(StandardCharsets.UTF_8));
            md.update("provider-binding-plan".getBytes(StandardCharsets.UTF_8));
            return new ProviderBindingPlanId(
                    "pbp-" + HexFormat.of().formatHex(md.digest()).substring(0, 16));
        } catch (Exception e) {
            return new ProviderBindingPlanId("pbp-" + capabilityGraphId);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
