package com.example.platform.product.domain;

import java.time.Instant;

/**
 * Render history entry at workspace level.
 * Links render jobs to projects for easy access.
 */
public record RenderHistory(
        String id,
        String workspaceId,
        String projectId,
        String renderJobId,
        String userId,
        String presetId,
        RenderHistoryStatus status,
        String outputUri,
        long durationMs,
        Instant createdAt,
        Instant completedAt
) {
    /**
     * Create a new render history entry.
     */
    public static RenderHistory create(String id, String workspaceId, String projectId,
                                        String renderJobId, String userId, String presetId) {
        return new RenderHistory(id, workspaceId, projectId, renderJobId, userId, presetId,
                RenderHistoryStatus.STARTED, null, 0, Instant.now(), null);
    }

    /**
     * Mark render as completed.
     */
    public RenderHistory complete(String outputUri, long durationMs) {
        return new RenderHistory(id, workspaceId, projectId, renderJobId, userId, presetId,
                RenderHistoryStatus.COMPLETED, outputUri, durationMs, createdAt, Instant.now());
    }

    /**
     * Mark render as failed.
     */
    public RenderHistory fail() {
        return new RenderHistory(id, workspaceId, projectId, renderJobId, userId, presetId,
                RenderHistoryStatus.FAILED, null, 0, createdAt, Instant.now());
    }

    /**
     * Check if render completed successfully.
     */
    public boolean isCompleted() {
        return status == RenderHistoryStatus.COMPLETED;
    }

    /**
     * Get duration in seconds.
     */
    public double getDurationSeconds() {
        return durationMs / 1000.0;
    }
}
