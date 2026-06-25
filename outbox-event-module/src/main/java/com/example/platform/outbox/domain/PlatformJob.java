package com.example.platform.outbox.domain;

import java.time.Instant;

/**
 * Domain model for a platform coordination job — orchestrates fan-out/fan-in across tasks.
 */
public record PlatformJob(
        String id,
        JobType jobType,
        String aggregateType,
        String aggregateId,
        String tenantId,
        String projectId,
        JobStatus status,
        int requiredMask,
        int completedMask,
        int failedMask,
        int totalTaskCount,
        int completedTaskCount,
        int failedTaskCount,
        String payloadJson,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public boolean isBarrierSatisfied() {
        return (completedMask & requiredMask) == requiredMask;
    }

    public boolean hasFailed() {
        return failedMask != 0;
    }

    public PlatformJob withStatus(JobStatus newStatus) {
        return new PlatformJob(id, jobType, aggregateType, aggregateId, tenantId, projectId,
                newStatus, requiredMask, completedMask, failedMask, totalTaskCount,
                completedTaskCount, failedTaskCount, payloadJson, metadataJson,
                createdAt, Instant.now(), newStatus == JobStatus.COMPLETED ? Instant.now() : completedAt);
    }
}
