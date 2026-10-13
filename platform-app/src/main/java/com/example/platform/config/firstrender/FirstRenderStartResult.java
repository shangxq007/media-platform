package com.example.platform.config.firstrender;

import java.util.Objects;

/**
 * Phase 6 result: the deterministic workflow id plus whether this call confirmed the workflow
 * started (fresh start or already-started).
 *
 * @param workflowId      the stable target workflow id ({@code render:<tenantId>:<renderJobId>})
 * @param workflowStarted whether this call confirmed the workflow started/already existed
 */
public record FirstRenderStartResult(String workflowId, boolean workflowStarted) {

    public FirstRenderStartResult {
        Objects.requireNonNull(workflowId, "workflowId");
    }
}
