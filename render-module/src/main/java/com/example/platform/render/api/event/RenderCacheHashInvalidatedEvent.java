package com.example.platform.render.api.event;

import java.time.Instant;
import java.util.List;

/**
 * Published when incremental reuse detects content-hash mismatch and forces re-execution.
 * Consumed by notification-module (in-app / email / webhook channels) and optional outbound webhook.
 */
public record RenderCacheHashInvalidatedEvent(
        String renderJobId,
        String projectId,
        String tenantId,
        String baseJobId,
        List<String> invalidatedTaskIds,
        int invalidatedCount,
        Instant detectedAt) {
 public RenderCacheHashInvalidatedEvent {RenderEventIdentity.require(renderJobId,"job");RenderEventIdentity.require(projectId,"project");RenderEventIdentity.require(tenantId,"tenant");RenderEventIdentity.require(baseJobId,"base job");invalidatedTaskIds=List.copyOf(invalidatedTaskIds);if(invalidatedTaskIds.isEmpty()||invalidatedCount!=invalidatedTaskIds.size())throw new IllegalArgumentException("invalidated task count mismatch");java.util.Objects.requireNonNull(detectedAt);}
 public String factKey(){return RenderEventIdentity.key("render.cache.invalidated",tenantId,projectId,renderJobId,baseJobId+":"+detectedAt);}
}
