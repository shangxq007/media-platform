package com.example.platform.render.domain.timeline.compile;

import java.util.List;

/**
 * Capability requirements for an artifact node.
 *
 * <p>Provider-neutral requirements that describe what capabilities
 * are needed to produce this artifact. Used by LogicalCapabilityGraph
 * for provider binding (future work).</p>
 *
 * @param requiredCapabilities  list of required capability codes
 * @param fidelityLevel         desired fidelity: DRAFT, PREVIEW, PRODUCTION
 * @param fallbackPolicy        what to do if requirements can't be met: REQUIRED, DEGRADE, SKIP
 */
public record ArtifactRequirement(
        List<String> requiredCapabilities,
        FidelityLevel fidelityLevel,
        FallbackPolicy fallbackPolicy) {

    /**
     * Creates a simple requirement with a single capability.
     */
    public static ArtifactRequirement of(String capability) {
        return new ArtifactRequirement(List.of(capability), FidelityLevel.PRODUCTION, FallbackPolicy.REQUIRED);
    }

    /**
     * Creates a requirement with multiple capabilities.
     */
    public static ArtifactRequirement of(List<String> capabilities) {
        return new ArtifactRequirement(capabilities, FidelityLevel.PRODUCTION, FallbackPolicy.REQUIRED);
    }

    /**
     * Creates a requirement with explicit fidelity and fallback.
     */
    public static ArtifactRequirement of(List<String> capabilities, FidelityLevel fidelity, FallbackPolicy fallback) {
        return new ArtifactRequirement(capabilities, fidelity, fallback);
    }

    /**
     * An empty requirement (no capabilities needed).
     */
    public static ArtifactRequirement empty() {
        return new ArtifactRequirement(List.of(), FidelityLevel.PRODUCTION, FallbackPolicy.REQUIRED);
    }

    /**
     * Fidelity level for render output.
     */
    public enum FidelityLevel {
        DRAFT,
        PREVIEW,
        PRODUCTION
    }

    /**
     * Fallback policy when requirements cannot be met.
     */
    public enum FallbackPolicy {
        /** Requirements must be met; fail if not. */
        REQUIRED,
        /** Degrade to lower fidelity if requirements can't be met. */
        DEGRADE,
        /** Skip this node if requirements can't be met. */
        SKIP
    }
}
