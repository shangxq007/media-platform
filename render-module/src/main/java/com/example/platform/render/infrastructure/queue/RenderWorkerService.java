package com.example.platform.render.infrastructure.queue;

import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProvider;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Render Worker Service - processes jobs from the queue.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Poll queue for jobs</li>
 *   <li>Acquire lease</li>
 *   <li>Execute job via its bound render provider</li>
 *   <li>Update state machine</li>
 *   <li>Persist artifact</li>
 *   <li>Release lease</li>
 * </ul>
 *
 * <p>Constraints:
 * <ul>
 *   <li>Single provider only (Provider)</li>
 *   <li>No fallback logic</li>
 *   <li>Deterministic execution only</li>
 * </ul>
 */
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "enabled", havingValue = "true")
public class RenderWorkerService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerService.class);

    private final RenderJobQueue queue;
    private final JobLeaseRepository leaseRepository;
    private final RenderJobRepository jobRepository;
    private final RenderProvider renderProvider;
    private final RenderJobStateMachine stateMachine;

    @Value("${render.worker.enabled:true}")
    private boolean workerEnabled;

    @Value("${render.worker.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${render.worker.id:worker-1}")
    private String workerId;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RenderWorkerService(
            RenderJobQueue queue,
            JobLeaseRepository leaseRepository,
            RenderJobRepository jobRepository,
            RenderProvider renderProvider,
            RenderJobStateMachine stateMachine) {
        this.queue = queue;
        this.leaseRepository = leaseRepository;
        this.jobRepository = jobRepository;
        this.renderProvider = renderProvider;
        this.stateMachine = stateMachine;
    }

    /**
     * Process the next job in the queue.
     * Returns true if a job was processed.
     */
    public boolean processNextJob() {
        if (!workerEnabled) {
            return false;
        }

        // Expire stale leases
        leaseRepository.expireStaleLeases();

        // Dequeue next job
        Optional<RenderJobQueue.QueuedJob> queuedJob = queue.dequeue();
        if (queuedJob.isEmpty()) {
            return false;
        }

        RenderJobQueue.QueuedJob job = queuedJob.get();
        log.info("Processing job {}", job.jobId());

        // Acquire lease
        Optional<JobLeaseRepository.JobLease> lease = leaseRepository.acquireLease(job.jobId(), workerId);
        if (lease.isEmpty()) {
            log.warn("Could not acquire lease for job {}, requeuing", job.jobId());
            queue.fail(job.jobId(), true);
            return false;
        }

        try {
            // Execute job
            executeJob(job, lease.get());
            return true;
        } catch (Exception e) {
            log.error("Failed to execute job {}", job.jobId(), e);
            handleJobFailure(job.jobId(), lease.get(), e);
            return false;
        }
    }

    /**
     * Execute a job with the Provider provider.
     */
    private void executeJob(RenderJobQueue.QueuedJob job, JobLeaseRepository.JobLease lease) {
        String jobId = job.jobId();
        Instant startTime = Instant.now();

        // Update state to EXECUTING
        jobRepository.updateStatus(jobId, RenderJobStatus.EXECUTING.name());
        log.info("Job {} state: EXECUTING", jobId);

        try {
            // Load job details
            Record jobRecord = renderJobRepository().requireJobRecord(jobId);
            String profile = jobRecord.get("profile", String.class);
            String aiScript = jobRecord.get("ai_script", String.class);

            // Execute via Provider
            RenderProvider.RenderResult result = renderProvider.render(jobId, aiScript, profile);

            // Calculate duration
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            long durationSeconds = durationMs / 1000;

            // Update state to COMPLETING
            jobRepository.updateStatus(jobId, RenderJobStatus.COMPLETING.name());
            log.info("Job {} state: COMPLETING", jobId);

            // Store artifact
            jobRepository.updateArtifactUri(jobId, result.storageUri());

            // Create billing record

            // Complete job
            jobRepository.updateStatus(jobId, RenderJobStatus.COMPLETED.name());
            queue.complete(jobId);
            leaseRepository.releaseLease(lease.leaseId(), "COMPLETED");

            log.info("Job {} completed successfully in {}ms", jobId, durationMs);

        } catch (Exception e) {
            throw new RuntimeException("Job execution failed", e);
        }
    }

    /**
     * Handle job failure.
     */
    private void handleJobFailure(String jobId, JobLeaseRepository.JobLease lease, Exception error) {
        // Update state to FAILED
        jobRepository.updateStatus(jobId, RenderJobStatus.FAILED.name());
        jobRepository.updateErrorMessage(jobId, error.getMessage());

        // Release lease
        leaseRepository.releaseLease(lease.leaseId(), "FAILED");

        // Mark queue entry as failed (no requeue for now)
        queue.fail(jobId, false);

        log.error("Job {} failed: {}", jobId, error.getMessage());
    }

    /**
     * Get the render job repository.
     */
    private RenderJobRepository renderJobRepository() {
        return jobRepository;
    }

    /**
     * Start the worker polling loop.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting render worker {}", workerId);
            // In production, this would be a scheduled task or thread
        }
    }

    /**
     * Stop the worker.
     */
    public void stop() {
        running.set(false);
        log.info("Stopping render worker {}", workerId);
    }

    /**
     * Check if worker is running.
     */
    public boolean isRunning() {
        return running.get();
    }
}
