package com.example.platform.render.infrastructure.providerruntime.fallback;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages retry policies for render execution.
 */
@Component
public class ExecutionRetryPolicy {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000;

    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<>();

    /**
     * Check if a provider should be retried.
     */
    public boolean shouldRetry(String providerName, String jobId) {
        String key = providerName + ":" + jobId;
        RetryState state = retryStates.get(key);

        if (state == null) {
            return true; // First attempt
        }

        return state.attempts() < MAX_RETRIES;
    }

    /**
     * Record a retry attempt.
     */
    public void recordAttempt(String providerName, String jobId) {
        String key = providerName + ":" + jobId;
        retryStates.compute(key, (k, v) -> {
            if (v == null) {
                return new RetryState(1, System.currentTimeMillis());
            }
            return new RetryState(v.attempts() + 1, System.currentTimeMillis());
        });
    }

    /**
     * Get the retry delay for a provider.
     */
    public long getRetryDelay(String providerName, String jobId) {
        String key = providerName + ":" + jobId;
        RetryState state = retryStates.get(key);

        if (state == null) {
            return 0;
        }

        // Exponential backoff
        return BASE_RETRY_DELAY_MS * (long) Math.pow(2, state.attempts() - 1);
    }

    /**
     * Reset retry state for a provider/job.
     */
    public void reset(String providerName, String jobId) {
        String key = providerName + ":" + jobId;
        retryStates.remove(key);
    }

    /**
     * Reset all retry states.
     */
    public void resetAll() {
        retryStates.clear();
    }

    /**
     * Retry state record.
     */
    private record RetryState(int attempts, long lastAttemptTime) {}
}
