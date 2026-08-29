package com.example.platform.render.domain.environment;

import java.util.List;
import java.util.Map;

/**
 * Immutable OpenCue submission request.
 *
 * <p>Contains only data that the existing model can reliably provide.
 * No credentials, no signed URLs, no mutable runtime state.
 *
 * @param renderJobId              RenderJob identifier (execution attempt identity)
 * @param timelineRevisionRef      immutable timeline revision reference
 * @param backendId                bound backend identifier
 * @param backendType              backend type from BackendExecutionSpec
 * @param environment              always OPEN_CUE for this request type
 * @param inputProductIds          input product identifiers
 * @param expectedOutputTypes      expected output product types
 * @param executionHints           safe execution hints (no secrets)
 * @param correlationId            idempotency/correlation reference
 * @param priority                 job priority
 * @param resourceRequirements     resource requirements
 */
public record OpenCueSubmissionRequest(
        String renderJobId,
        String timelineRevisionRef,
        String backendId,
        String backendType,
        String environment,
        List<String> inputProductIds,
        List<String> expectedOutputTypes,
        Map<String, String> executionHints,
        String correlationId,
        int priority,
        Map<String, Object> resourceRequirements) {

    /**
     * Build a submission request from an ExecutionJob.
     * Preserves the job's selected backend exactly.
     *
     * @param job           the compiled ExecutionJob
     * @param timelineRef   immutable timeline revision reference
     * @return immutable submission request
     * @throws IllegalArgumentException if job is null or has no tasks
     */
    public static OpenCueSubmissionRequest fromExecutionJob(
            com.example.platform.render.domain.execution.ExecutionJob job,
            String timelineRef) {
        if (job == null) throw new IllegalArgumentException("ExecutionJob must not be null");
        if (job.tasks() == null || job.tasks().isEmpty())
            throw new IllegalArgumentException("ExecutionJob must have at least one task");

        var task = job.tasks().get(0);
        var spec = task.backendSpec();

        List<String> inputIds = spec != null && spec.inputProductIds() != null
                ? List.copyOf(spec.inputProductIds()) : List.of();
        List<String> outputTypes = spec != null && spec.expectedOutputs() != null
                ? spec.expectedOutputs().stream()
                    .map(com.example.platform.render.domain.execution.ExecutionOutput::expectedProductType)
                    .toList() : List.of();
        Map<String, String> hints = spec != null && spec.executionHints() != null
                ? Map.copyOf(spec.executionHints()) : Map.of();

        return new OpenCueSubmissionRequest(
                job.jobId(),
                timelineRef,
                job.backendId(),
                job.backendType(),
                "OPEN_CUE",
                inputIds,
                outputTypes,
                hints,
                "corr-" + job.jobId(),
                job.priority(),
                job.resourceRequirements() != null ? Map.copyOf(job.resourceRequirements()) : Map.of()
        );
    }

    /** Returns true when this request carries a concrete bound backend identity. */
    public boolean hasBoundBackendIdentity() {
        return isBoundBackendIdentity(backendId);
    }

    /**
     * Validates structural binding only. Concrete identity and capability
     * authority belong to provider registry layers, not OpenCue.
     */
    public static boolean isBoundBackendIdentity(String backendId) {
        if (backendId == null) {
            return false;
        }
        String normalized = backendId.trim();
        return !normalized.isEmpty() && !"provider".equalsIgnoreCase(normalized);
    }
}
