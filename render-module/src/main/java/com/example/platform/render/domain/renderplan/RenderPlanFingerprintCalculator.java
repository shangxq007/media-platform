package com.example.platform.render.domain.renderplan;

import java.util.List;

/**
 * Computes the deterministic {@link RenderPlanFingerprint} (C7). Pure function of
 * the plan's canonical encoding; excludes request id, plan id, status, diagnostics,
 * resolution state, capability context, provenance, execution requirements,
 * timestamps, and provider/worker/device properties.
 */
public final class RenderPlanFingerprintCalculator {

    private static final RenderPlanCanonicalCodec CODEC = RenderPlanCanonicalCodec.INSTANCE;

    private RenderPlanFingerprintCalculator() {
    }

    /** Computes the plan fingerprint from the plan's canonical encoding. */
    public static RenderPlanFingerprint compute(RenderPlan plan) {
        String canonical = CODEC.planFingerprintCanonical(plan);
        return new RenderPlanFingerprint(CODEC.sha256Hex(canonical));
    }

    /**
     * Computes the plan fingerprint directly from the plan's ingredients (C7),
     * without requiring a constructed RenderPlan. Used by the planner, which must
     * compute the fingerprint before assembling the plan record.
     */
    public static RenderPlanFingerprint compute(
            TimelineRevisionReference revision,
            RenderRequest request,
            List<RenderNode> nodes,
            List<RenderDependencyEdge> edges) {
        String canonical = CODEC.planFingerprintCanonical(revision, request, nodes, edges);
        return new RenderPlanFingerprint(CODEC.sha256Hex(canonical));
    }

    /** Computes the node requirements fingerprint (C6) for a non-effect node. */
    public static String computeNodeRequirementsFingerprint(RenderNode node) {
        return CODEC.sha256Hex(CODEC.nodeRequirementsFingerprintCanonical(node));
    }

    /** Shared codec instance. */
    public static RenderPlanCanonicalCodec codec() {
        return CODEC;
    }
}
