package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.example.platform.shared.events.RenderJobFailedEvent;
import org.springframework.context.ApplicationEventPublisher;

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
    private final ApplicationEventPublisher eventPublisher;

    public RenderWorkerRecoveryService(RenderJobRepository renderJobRepository,
            ApplicationEventPublisher eventPublisher) {
        this.renderJobRepository = renderJobRepository;
        this.eventPublisher = eventPublisher;
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

            if ("REQUEUE".equalsIgnoreCase(action)) {
                // First mark the old job as FAILED, then create a new retry job
                int marked = renderJobRepository.markExecutingJobFailed(jobId, reason);
                if (marked > 0) {
                    String projectId = job.get("project_id", String.class);
                    eventPublisher.publishEvent(new RenderJobFailedEvent(
                            jobId, projectId, reason, Instant.now(),
                            RenderJobRepository.initiatorFrom(job)));

                    String newJobId = com.example.platform.shared.Ids.newId("rj");
                    renderJobRepository.createRetryJob(newJobId, jobId);
                    log.info("Created retry job {} for stale job {}", newJobId, jobId);
                    recovered++;
                }
            } else {
                int updated = renderJobRepository.markExecutingJobFailed(jobId, reason);
                if (updated > 0) {
                    eventPublisher.publishEvent(new RenderJobFailedEvent(
                            jobId,
                            job.get("project_id", String.class),
                            reason,
                            Instant.now(),
                            RenderJobRepository.initiatorFrom(job)));
                    log.info("Marked stale job FAILED: {}", jobId);
                    recovered++;
                }
            }
        }

        log.info("Recovered {} stale EXECUTING jobs", recovered);
        return recovered;
    }
}
