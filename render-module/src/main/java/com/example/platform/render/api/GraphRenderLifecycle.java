package com.example.platform.render.api;

import com.example.platform.artifact.app.ArtifactOutputReference;
import java.util.Optional;

/** Render-owned fenced lifecycle for one immutable bound-graph execution. Not an HTTP authority. */
public interface GraphRenderLifecycle {
    /** Diagnostic progress, not a second job status or completion authority. */
    enum PreparationStage { INPUTS, SNAPSHOT, PLANNING, BINDING, BOUND_GRAPH_SAVE, DISPATCH }
    void preparationStage(String tenantId, String jobId, PreparationStage stage);
    void failPreparation(String tenantId, String jobId, PreparationStage stage);

    record Execution(String tenantId, String projectId, String jobId, String planRef,
            String planDigest, String graphDigest, String attemptId, long generation,
            String finalTaskId) {}
    record Output(String taskId, String taskAttemptId, long taskGeneration, String artifactId) {}
    record Status(Execution execution, String status, String finalArtifactId, String failureCode, Output boundOutput) {}

    Execution begin(String tenantId, String jobId, String planRef, String planDigest,
            String graphDigest, String finalTaskId);
    /** Holds the canonical Job lock until the caller's publication transaction ends; rejects stale/terminal execution. */
    void requireExecuting(Execution execution);
    void bindOutput(Execution execution, Output output);
    ArtifactOutputReference complete(Execution execution, Output output);
    void fail(String tenantId, String jobId, String planRef, String planDigest,
            String graphDigest, boolean cancelled);
    Optional<Status> find(String tenantId, String projectId, String jobId);
}
