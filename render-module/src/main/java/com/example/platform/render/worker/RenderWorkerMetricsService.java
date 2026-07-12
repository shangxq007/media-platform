package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderJobLifecycleEventRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Lightweight metrics service for RenderJob execution health.
 */
@Service
public class RenderWorkerMetricsService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerMetricsService.class);

    private final RenderJobRepository renderJobRepository;
    private final RenderJobLifecycleEventRepository eventRepository;
    private final RenderWorkerRecoveryService recoveryService;

    public RenderWorkerMetricsService(
            RenderJobRepository renderJobRepository,
            RenderJobLifecycleEventRepository eventRepository,
            RenderWorkerRecoveryService recoveryService) {
        this.renderJobRepository = renderJobRepository;
        this.eventRepository = eventRepository;
        this.recoveryService = recoveryService;
    }

    /**
     * Get execution summary for a project.
     */
    public Map<String, Object> getProjectExecutionSummary(String projectId, Duration lookback) {
        Instant now = Instant.now();
        Instant lookbackSince = now.minus(lookback);

        // State counts
        Map<String, Integer> stateCounts = renderJobRepository.countByStatus(projectId);

        // Health counts
        int staleExecuting = renderJobRepository.countStaleExecuting(projectId, now.minus(Duration.ofMinutes(30)));
        int retryEligible = renderJobRepository.countRetryEligibleFailed(projectId);
        int retryExhausted = renderJobRepository.countRetryExhausted(projectId);

        // Oldest job ages
        Long oldestQueuedAgeSeconds = null;
        Long oldestExecutingAgeSeconds = null;
        try {
            Instant oldestQueued = renderJobRepository.oldestQueuedCreatedAt(projectId);
            if (oldestQueued != null) oldestQueuedAgeSeconds = Duration.between(oldestQueued, now).getSeconds();
        } catch (Exception e) { /* ignore if no queued jobs */ }
        try {
            Instant oldestExecuting = renderJobRepository.oldestExecutingUpdatedAt(projectId);
            if (oldestExecuting != null) oldestExecutingAgeSeconds = Duration.between(oldestExecuting, now).getSeconds();
        } catch (Exception e) { /* ignore if no executing jobs */ }

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("projectId", projectId);
        response.put("lookback", lookback.toString());
        response.put("generatedAt", now.toString());

        response.put("stateCounts", stateCounts);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("staleExecuting", staleExecuting);
        health.put("retryEligibleFailed", retryEligible);
        health.put("retryExhausted", retryExhausted);
        health.put("oldestQueuedAgeSeconds", oldestQueuedAgeSeconds);
        health.put("oldestExecutingAgeSeconds", oldestExecutingAgeSeconds);
        response.put("health", health);

        // Warnings
        List<String> warnings = new ArrayList<>();
        if (staleExecuting > 0) warnings.add("STALE_EXECUTING_JOBS_PRESENT");
        if (retryEligible > 0) warnings.add("RETRY_ELIGIBLE_FAILED_JOBS_PRESENT");
        if (retryExhausted > 0) warnings.add("RETRY_EXHAUSTED_JOBS_PRESENT");
        if (oldestQueuedAgeSeconds != null && oldestQueuedAgeSeconds > 3600) warnings.add("OLD_QUEUED_JOB");
        response.put("warnings", warnings);

        return response;
    }
}
