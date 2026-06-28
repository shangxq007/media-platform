package com.example.platform.render.domain.timeline.compile.executionplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Deterministic identifier for a RenderExecutionStep.
 *
 * <p>Derived from the plan ID, step type, and node ID to ensure stability.</p>
 */
public record RenderExecutionStepId(String value) {

    /**
     * Creates a deterministic step ID.
     */
    public static RenderExecutionStepId of(String planId, String stepType, String nodeId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(planId.getBytes(StandardCharsets.UTF_8));
            md.update(stepType.getBytes(StandardCharsets.UTF_8));
            if (nodeId != null) {
                md.update(nodeId.getBytes(StandardCharsets.UTF_8));
            }
            return new RenderExecutionStepId(
                    "res-" + HexFormat.of().formatHex(md.digest()).substring(0, 16));
        } catch (Exception e) {
            return new RenderExecutionStepId("res-" + planId + "-" + stepType);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
