package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Minimal recovery service for stale EXECUTING RenderJobs.
 * 
 * Recovers jobs stuck in EXECUTING after worker crash or process loss.
 * Default action: mark as FAILED.
 */
@Service
public class RenderWorkerRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerRecoveryService.class);

    private final RenderJobRepository renderJobRepository;

    public RenderWorkerRecoveryService(RenderJobRepository renderJobRepository) {
        this.renderJobRepository = renderJobRepository;
    }

    /**
     * Recover stale EXECUTING jobs.
     * 
     * @param staleTimeout duration after which EXECUTING job is considered stale
     * @param action       recovery action: "FAIL" or "REQUEUE"
     * @param limit        max jobs to recover per call
     * @return number of jobs recovered
     */
    public int recoverStaleJobs(Duration staleTimeout, String action, int limit) {
        Instant cutoff = Instant.now().minus(staleTimeout);
        List<Record> staleJobs = renderJobRepository.findStaleExecutingJobs(cutoff, limit);

        if (staleJobs.isEmpty()) {
            return 0;
        }

        log.info("Found {} stale EXECUTING jobs (cutoff: {})", staleJobs.size(), cutoff);
        int recovered = 0;

        for (Record job : staleJobs) {
            String jobId = job.get("id", String.class);
            String reason = "Stale worker recovery: EXECUTING since before " + cutoff;

            int updated;
            if ("REQUEUE".equalsIgnoreCase(action)) {
                updated = renderJobRepository.requeueExecutingJob(jobId, reason);
                if (updated > 0) {
                    log.info("Requeued stale job: {}", jobId);
                }
            } else {
                updated = renderJobRepository.markExecutingJobFailed(jobId, reason);
                if (updated > 0) {
                    log.info("Marked stale job FAILED: {}", jobId);
                }
            }

            if (updated > 0) {
                recovered++;
            }
        }

        log.info("Recovered {} stale EXECUTING jobs", recovered);
        return recovered;
    }
}
