package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * Calculates deterministic digests for execution plans.
 *
 * <p>Covers: schemaVersion, timelineRevisionId, timelineRevisionDigest, inputs,
 * operations, dependencies, outputs, resources, capabilities, determinism.
 * Excludes: wall-clock time, creation context, runtime state.
 */
public final class ExecutionPlanDigestCalculator {

    private ExecutionPlanDigestCalculator() {
    }

    /**
     * Calculates the digest for an execution plan.
     *
     * @param plan the plan to digest
     * @return the computed digest
     */
    public static ExecutionPlanDigest calculate(MediaExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        String canonical = buildCanonicalDigestInput(plan);
        String hash = ExecutionPlanCanonicalSerializer.sha256Hex(canonical);
        return new ExecutionPlanDigest(hash);
    }

    /**
     * Builds the canonical string used as input for digest calculation.
     * Only includes fields that affect the plan's execution semantics.
     */
    public static String buildCanonicalDigestInput(MediaExecutionPlan plan) {
        StringBuilder sb = new StringBuilder("planDigest{");
        sb.append("schema=").append(plan.schemaVersion().value());
        sb.append(",rev=").append(plan.timelineRevisionId());
        sb.append(",revDigest=").append(plan.timelineRevisionDigest());

        // Inputs (sorted for determinism)
        sb.append(",inputs=[");
        plan.inputs().stream()
                .map(ExecutionInputBinding::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        // Steps (sorted for determinism)
        sb.append(",steps=[");
        plan.steps().stream()
                .map(MediaExecutionStep::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        // Edges (sorted for determinism)
        sb.append(",edges=[");
        plan.edges().stream()
                .map(ExecutionDependency::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        // Outputs (sorted for determinism)
        sb.append(",outputs=[");
        plan.outputs().stream()
                .map(ExecutionOutputDeclaration::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append('}');
        return sb.toString();
    }

    /**
     * Verifies that a plan matches its stored digest.
     *
     * @return true if the plan's content matches its digest
     */
    public static boolean verifyDigest(MediaExecutionPlan plan) {
        ExecutionPlanDigest calculated = calculate(plan);
        return calculated.equals(plan.digest());
    }
}
