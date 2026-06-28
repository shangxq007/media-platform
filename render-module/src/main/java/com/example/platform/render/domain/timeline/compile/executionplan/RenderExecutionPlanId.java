package com.example.platform.render.domain.timeline.compile.executionplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Deterministic identifier for a RenderExecutionPlan.
 *
 * <p>Derived from the source ProviderBindingPlan ID and execution policy
 * to ensure reproducibility.</p>
 */
public record RenderExecutionPlanId(String value) {

    /**
     * Creates a deterministic plan ID from a binding plan ID and policy mode.
     */
    public static RenderExecutionPlanId fromBindingPlan(String bindingPlanId, String policyMode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bindingPlanId.getBytes(StandardCharsets.UTF_8));
            md.update("render-execution-plan".getBytes(StandardCharsets.UTF_8));
            md.update(policyMode.getBytes(StandardCharsets.UTF_8));
            return new RenderExecutionPlanId(
                    "rep-" + HexFormat.of().formatHex(md.digest()).substring(0, 16));
        } catch (Exception e) {
            return new RenderExecutionPlanId("rep-" + bindingPlanId);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
