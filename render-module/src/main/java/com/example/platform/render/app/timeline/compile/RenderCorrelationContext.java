package com.example.platform.render.app.timeline.compile;

import java.util.List;
import java.util.UUID;

/**
 * Internal render correlation context — carries stable identifiers
 * through the render pipeline for observability and debugging.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>Immutable. Does not include raw commands, storage paths,
 * process environment, secrets, or credentials.</p>
 */
public record RenderCorrelationContext(
        String renderCorrelationId,
        String renderRequestFingerprint,
        String projectId,
        String timelineRevisionId,
        String executionMode,
        String renderJobId,
        String artifactGraphId,
        String capabilityGraphId,
        String providerBindingPlanId,
        String renderExecutionPlanId,
        String localExecutionRunId,
        List<String> inputProductIds,
        String outputProductId,
        String futureOpenCueJobId) {

    /**
     * Create initial correlation context at facade boundary.
     */
    public static RenderCorrelationContext create(String projectId, String timelineRevisionId,
                                                    String executionMode) {
        return new RenderCorrelationContext(
                UUID.randomUUID().toString(),
                null, projectId, timelineRevisionId, executionMode,
                null, null, null, null, null, null, null, null, null);
    }

    /**
     * Return a copy with fingerprint attached.
     */
    public RenderCorrelationContext withFingerprint(String fingerprint) {
        return new RenderCorrelationContext(
                renderCorrelationId, fingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with render job ID attached.
     */
    public RenderCorrelationContext withRenderJobId(String renderJobId) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with graph IDs attached.
     */
    public RenderCorrelationContext withGraphIds(String artifactGraphId, String capabilityGraphId) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with binding/plan IDs attached.
     */
    public RenderCorrelationContext withPlanIds(String providerBindingPlanId,
                                                  String renderExecutionPlanId) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with local execution run ID attached.
     */
    public RenderCorrelationContext withLocalExecutionRunId(String localExecutionRunId) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with output product ID attached.
     */
    public RenderCorrelationContext withOutputProductId(String outputProductId) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Return a copy with input product IDs attached.
     */
    public RenderCorrelationContext withInputProductIds(List<String> inputProductIds) {
        return new RenderCorrelationContext(
                renderCorrelationId, renderRequestFingerprint, projectId, timelineRevisionId,
                executionMode, renderJobId, artifactGraphId, capabilityGraphId,
                providerBindingPlanId, renderExecutionPlanId, localExecutionRunId,
                inputProductIds, outputProductId, futureOpenCueJobId);
    }

    /**
     * Snapshot for audit events — safe immutable copy.
     */
    public RenderCorrelationContext snapshot() {
        return this; // already immutable
    }

    /**
     * Returns true if this context has a fingerprint.
     */
    public boolean hasFingerprint() {
        return renderRequestFingerprint != null;
    }
}
