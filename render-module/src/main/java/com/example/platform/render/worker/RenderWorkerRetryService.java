package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Minimal retry service for transient RenderJob failures.
 * 
 * Requeues FAILED jobs marked as retryable.
 */
@Service
public class RenderWorkerRetryService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerRetryService.class);

    private final RenderJobRepository renderJobRepository;

    public RenderWorkerRetryService(RenderJobRepository renderJobRepository) {
        this.renderJobRepository = renderJobRepository;
    }

    /**
     * Requeue retry-eligible FAILED jobs.
     * 
     * @param limit max jobs to requeue per call
     * @return number of jobs requeued
     */
    public int requeueRetryEligibleJobs(int limit) {
        List<Record> jobs = renderJobRepository.findRetryEligibleFailedJobs(Instant.now(), limit);

        if (jobs.isEmpty()) {
            return 0;
        }

        log.info("Found {} retry-eligible FAILED jobs", jobs.size());
        int requeued = 0;

        for (Record job : jobs) {
            String jobId = job.get("id", String.class);
            int updated = renderJobRepository.requeueFailedJob(jobId);
            if (updated > 0) {
                log.info("Requeued retry-eligible job: {}", jobId);
                requeued++;
            }
        }

        log.info("Requeued {} retry-eligible jobs", requeued);
        return requeued;
    }

    /**
     * Classify failure as retryable or non-retryable.
     * 
     * @param error the exception
     * @return true if retryable
     */
    public boolean isRetryable(Exception error) {
        String message = error.getMessage();
        if (message == null) return false;

        // Transient/retryable failures
        if (message.contains("timeout") || message.contains("Timeout")) return true;
        if (message.contains("connection") || message.contains("Connection")) return true;
        if (message.contains("interrupted") || message.contains("Interrupted")) return true;
        if (message.contains("killed") || message.contains("Killed")) return true;
        if (message.contains("temporary") || message.contains("Temporary")) return true;

        // Non-retryable failures
        if (message.contains("validation") || message.contains("Validation")) return false;
        if (message.contains("not found") || message.contains("Not found")) return false;
        if (message.contains("invalid") || message.contains("Invalid")) return false;
        if (message.contains("unsupported") || message.contains("Unsupported")) return false;

        // Default: non-retryable for unknown failures
        return false;
    }

    /**
     * Format failure message with retryable marker.
     * 
     * @param error     the exception
     * @param retryable whether retryable
     * @return formatted error message
     */
    public String formatFailureMessage(Exception error, boolean retryable) {
        String marker = retryable ? "RETRYABLE" : "TERMINAL";
        return "[" + marker + "] " + error.getMessage();
    }
}
