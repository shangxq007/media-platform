package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * A single policy violation found by the RenderPlanPolicyGuard.
 *
 * <p>Internal only — captures the violation type, affected step/node,
 * and a human-readable message.</p>
 *
 * @param type      type of violation
 * @param stepId    affected step ID (null for plan-level violations)
 * @param nodeId    affected capability node ID (null for plan-level violations)
 * @param message   human-readable violation description
 */
public record RenderPlanPolicyViolation(
        RenderPlanPolicyViolationType type,
        String stepId,
        String nodeId,
        String message) {

    /**
     * Returns true if this violation is about a specific step.
     */
    public boolean hasStep() {
        return stepId != null;
    }

    /**
     * Returns true if this violation is about a specific node.
     */
    public boolean hasNode() {
        return nodeId != null;
    }
}
