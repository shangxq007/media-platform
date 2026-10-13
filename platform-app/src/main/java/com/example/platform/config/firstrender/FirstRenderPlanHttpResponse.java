package com.example.platform.config.firstrender;

/**
 * First Render plan HTTP response body.
 *
 * <p>Echoes the three independent identities plus the planning-only status ({@code "PLANNED"}), and
 * reports the target workflow id and whether this call confirmed the workflow started. It makes no
 * claim that planning executed, that work was queued, or that a render completed — {@code status}
 * is {@code "PLANNED"}, not the persisted render-job status.
 */
public record FirstRenderPlanHttpResponse(
        String renderJobId,
        String requestId,
        String executionPlanId,
        String status,
        String workflowId,
        boolean workflowStarted) {
}
