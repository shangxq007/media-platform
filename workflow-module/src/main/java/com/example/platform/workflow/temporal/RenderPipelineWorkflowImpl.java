package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

public class RenderPipelineWorkflowImpl implements RenderPipelineWorkflow.RenderPipeline {
    private static final Logger log = LoggerFactory.getLogger(RenderPipelineWorkflowImpl.class);

    private final RenderPipelineActivities.RenderActivity renderActivity =
            Workflow.newActivityStub(RenderPipelineActivities.RenderActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(30))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(5))
                                    .build())
                            .build());

    private final RenderPipelineActivities.StorageActivity storageActivity =
            Workflow.newActivityStub(RenderPipelineActivities.StorageActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(10))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    private final RenderPipelineActivities.NotificationActivity notificationActivity =
            Workflow.newActivityStub(RenderPipelineActivities.NotificationActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(5)
                                    .build())
                            .build());

    private final RenderPipelineActivities.AuditActivity auditActivity =
            Workflow.newActivityStub(RenderPipelineActivities.AuditActivity.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    private boolean cancelled = false;

    @Override
    public String execute(String jobId, String timelineJson, String profile) {
        log.info("Temporal Workflow: starting render pipeline for job={}", jobId);
        auditActivity.recordAudit(jobId, "WORKFLOW_START", "RUNNING", "Pipeline started");
        try {
            if (cancelled) throw new RuntimeException("Workflow cancelled");
            String outputPath = renderActivity.renderJob(jobId, timelineJson, profile);
            auditActivity.recordAudit(jobId, "RENDER_COMPLETE", "RUNNING", "Render output: " + outputPath);
            if (cancelled) throw new RuntimeException("Workflow cancelled");
            String artifactUrl = storageActivity.storeArtifact(jobId, outputPath, "mp4");
            auditActivity.recordAudit(jobId, "STORAGE_COMPLETE", "RUNNING", "Artifact: " + artifactUrl);
            notificationActivity.notifyCompletion(jobId, "COMPLETED", artifactUrl);
            auditActivity.recordAudit(jobId, "WORKFLOW_COMPLETE", "COMPLETED", "Pipeline completed");
            return artifactUrl;
        } catch (Exception e) {
            log.error("Temporal Workflow: failed for job={}", jobId, e);
            auditActivity.recordAudit(jobId, "WORKFLOW_FAILED", "FAILED", e.getMessage());
            notificationActivity.notifyCompletion(jobId, "FAILED", e.getMessage());
            throw e;
        }
    }

    @Override
    public void cancel(String reason) {
        this.cancelled = true;
        log.warn("Temporal Workflow: cancellation requested: {}", reason);
    }

    @Override
    public void retry(String nodeId) {
        log.info("Temporal Workflow: retry requested for node: {}", nodeId);
    }
}
