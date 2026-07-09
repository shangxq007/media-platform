package com.example.platform.render.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Records lifecycle events for RenderJob execution.
 * 
 * Events are stored in-memory for diagnostics. For production,
 * events should be persisted to RenderJob metadata or a dedicated table.
 */
@Service
public class RenderJobLifecycleEventService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobLifecycleEventService.class);
    private static final int MAX_EVENTS_PER_JOB = 50;

    // In-memory event store for diagnostics
    private final Map<String, List<LifecycleEvent>> eventStore = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<LifecycleEvent>> eldest) {
            return size() > 1000; // Max 1000 jobs tracked
        }
    };

    public record LifecycleEvent(
            String eventType,
            String statusFrom,
            String statusTo,
            Instant timestamp,
            String workerId,
            int attempt,
            String reasonCode,
            String reason,
            boolean retryable,
            String outputProductId,
            Long durationMs
    ) {}

    public void recordJobClaimed(String jobId, String workerId, int attempt) {
        appendEvent(jobId, new LifecycleEvent(
                "JOB_CLAIMED", "QUEUED", "EXECUTING", Instant.now(),
                workerId, attempt, null, null, false, null, null));
        log.info("Lifecycle: JOB_CLAIMED jobId={} workerId={} attempt={}", jobId, workerId, attempt);
    }

    public void recordClaimLost(String jobId, String workerId) {
        appendEvent(jobId, new LifecycleEvent(
                "CLAIM_LOST", "QUEUED", null, Instant.now(),
                workerId, 0, "RACE", "Claim lost to another worker", false, null, null));
        log.info("Lifecycle: CLAIM_LOST jobId={} workerId={}", jobId, workerId);
    }

    public void recordExecutionStarted(String jobId, String workerId, int attempt) {
        appendEvent(jobId, new LifecycleEvent(
                "EXECUTION_STARTED", "EXECUTING", null, Instant.now(),
                workerId, attempt, null, null, false, null, null));
        log.info("Lifecycle: EXECUTION_STARTED jobId={} workerId={} attempt={}", jobId, workerId, attempt);
    }

    public void recordExecutionCompleted(String jobId, String workerId, String outputProductId, long durationMs) {
        appendEvent(jobId, new LifecycleEvent(
                "EXECUTION_COMPLETED", "EXECUTING", "COMPLETED", Instant.now(),
                workerId, 0, null, null, false, outputProductId, durationMs));
        log.info("Lifecycle: EXECUTION_COMPLETED jobId={} workerId={} output={} durationMs={}",
                jobId, workerId, outputProductId, durationMs);
    }

    public void recordExecutionFailed(String jobId, String workerId, String failureCode, String safeReason, boolean retryable) {
        appendEvent(jobId, new LifecycleEvent(
                "EXECUTION_FAILED", "EXECUTING", "FAILED", Instant.now(),
                workerId, 0, failureCode, safeReason, retryable, null, null));
        log.info("Lifecycle: EXECUTION_FAILED jobId={} workerId={} code={} retryable={}",
                jobId, workerId, failureCode, retryable);
    }

    public void recordRecoveredStale(String jobId, String action, String safeReason) {
        appendEvent(jobId, new LifecycleEvent(
                "JOB_RECOVERED_STALE", "EXECUTING", action, Instant.now(),
                null, 0, "STALE_RECOVERY", safeReason, false, null, null));
        log.info("Lifecycle: JOB_RECOVERED_STALE jobId={} action={}", jobId, action);
    }

    public void recordRequeuedForRetry(String jobId, int attempt) {
        appendEvent(jobId, new LifecycleEvent(
                "JOB_REQUEUED_FOR_RETRY", "FAILED", "QUEUED", Instant.now(),
                null, attempt, "RETRY", "Requeued for retry", false, null, null));
        log.info("Lifecycle: JOB_REQUEUED_FOR_RETRY jobId={} attempt={}", jobId, attempt);
    }

    public void recordRetryExhausted(String jobId, String safeReason) {
        appendEvent(jobId, new LifecycleEvent(
                "RETRY_EXHAUSTED", "FAILED", null, Instant.now(),
                null, 0, "RETRY_EXHAUSTED", safeReason, false, null, null));
        log.info("Lifecycle: RETRY_EXHAUSTED jobId={}", jobId);
    }

    public List<LifecycleEvent> getEvents(String jobId) {
        return eventStore.getOrDefault(jobId, Collections.emptyList());
    }

    private void appendEvent(String jobId, LifecycleEvent event) {
        eventStore.computeIfAbsent(jobId, k -> new ArrayList<>()).add(event);
        // Bound history
        List<LifecycleEvent> events = eventStore.get(jobId);
        if (events.size() > MAX_EVENTS_PER_JOB) {
            events.remove(0);
        }
    }
}
