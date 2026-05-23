package com.example.platform.render.app;

import java.time.Instant;

public record RenderWorkerQueueJob(
        String jobId,
        String tenantId,
        String profile,
        String workerType,
        Instant enqueuedAt) {}
