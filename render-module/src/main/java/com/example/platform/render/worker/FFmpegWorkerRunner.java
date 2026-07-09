package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobRepository;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FFmpeg render worker supporting CLI once and polling modes.
 * 
 * Once mode:
 *   java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
 *        --render.worker.job-id=rj_xxx
 * 
 * Poll mode:
 *   java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
 *        --render.worker.mode=poll --render.worker.poll-interval=5s
 */
@Component
@ConditionalOnProperty(name = "render.worker.enabled", havingValue = "true")
public class FFmpegWorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FFmpegWorkerRunner.class);

    private final RenderWorkerExecutionService workerExecutionService;
    private final RenderJobRepository renderJobRepository;
    private final RenderWorkerRecoveryService recoveryService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public FFmpegWorkerRunner(
            RenderWorkerExecutionService workerExecutionService,
            RenderJobRepository renderJobRepository,
            RenderWorkerRecoveryService recoveryService) {
        this.workerExecutionService = workerExecutionService;
        this.renderJobRepository = renderJobRepository;
        this.recoveryService = recoveryService;
    }

    @Override
    public void run(String... args) throws Exception {
        String jobId = System.getProperty("render.worker.job-id",
                System.getenv().getOrDefault("RENDER_WORKER_JOB_ID", ""));
        String mode = System.getProperty("render.worker.mode",
                System.getenv().getOrDefault("RENDER_WORKER_MODE", "once"));
        String pollIntervalStr = System.getProperty("render.worker.poll-interval",
                System.getenv().getOrDefault("RENDER_WORKER_POLL_INTERVAL", "5s"));

        // Register shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("FFmpeg Worker shutdown requested");
            running.set(false);
        }));

        if ("poll".equalsIgnoreCase(mode)) {
            runPollMode(pollIntervalStr);
        } else {
            runOnceMode(jobId);
        }
    }

    private void runOnceMode(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            log.error("No job ID specified. Use --render.worker.job-id=rj_xxx or RENDER_WORKER_JOB_ID env var");
            System.exit(1);
        }

        log.info("FFmpeg Worker [once] starting for job: {}", jobId);
        
        try {
            String resultJobId = workerExecutionService.executeOnce(jobId);
            log.info("FFmpeg Worker [once] completed successfully: jobId={}", resultJobId);
            System.exit(0);
        } catch (Exception e) {
            log.error("FFmpeg Worker [once] failed for job {}: {}", jobId, e.getMessage());
            System.exit(1);
        }
    }

    private void runPollMode(String pollIntervalStr) {
        Duration pollInterval = parseDuration(pollIntervalStr);
        boolean recoveryEnabled = Boolean.parseBoolean(
                System.getProperty("render.worker.recovery.enabled",
                        System.getenv().getOrDefault("RENDER_WORKER_RECOVERY_ENABLED", "true")));
        Duration staleTimeout = parseDuration(
                System.getProperty("render.worker.recovery.stale-timeout",
                        System.getenv().getOrDefault("RENDER_WORKER_RECOVERY_STALE_TIMEOUT", "30m")));
        String recoveryAction = System.getProperty("render.worker.recovery.action",
                System.getenv().getOrDefault("RENDER_WORKER_RECOVERY_ACTION", "FAIL"));

        log.info("FFmpeg Worker [poll] starting with interval: {}, recovery: {}", pollInterval, recoveryEnabled);

        // Run recovery on startup
        if (recoveryEnabled) {
            try {
                int recovered = recoveryService.recoverStaleJobs(staleTimeout, recoveryAction, 10);
                if (recovered > 0) {
                    log.info("Startup recovery: recovered {} stale jobs", recovered);
                }
            } catch (Exception e) {
                log.warn("Startup recovery failed: {}", e.getMessage());
            }
        }

        while (running.get()) {
            try {
                // Find oldest QUEUED RenderJob
                Record job = findQueuedJob();
                
                if (job == null) {
                    // No jobs available, run recovery and sleep
                    if (recoveryEnabled) {
                        try {
                            recoveryService.recoverStaleJobs(staleTimeout, recoveryAction, 5);
                        } catch (Exception e) {
                            log.debug("Recovery cycle failed: {}", e.getMessage());
                        }
                    }
                    Thread.sleep(pollInterval.toMillis());
                    continue;
                }

                String jobId = job.get("id", String.class);
                log.info("FFmpeg Worker [poll] found QUEUED job: {}", jobId);

                // Try to claim by transitioning QUEUED -> EXECUTING
                boolean claimed = tryClaimJob(jobId);
                if (!claimed) {
                    log.info("FFmpeg Worker [poll] job {} already claimed, skipping", jobId);
                    continue;
                }

                // Execute
                try {
                    String resultJobId = workerExecutionService.executeOnce(jobId);
                    log.info("FFmpeg Worker [poll] completed job: {}", resultJobId);
                } catch (Exception e) {
                    log.error("FFmpeg Worker [poll] failed job {}: {}", jobId, e.getMessage());
                    // Mark FAILED if not already
                    markFailed(jobId, e.getMessage());
                }

            } catch (InterruptedException e) {
                log.info("FFmpeg Worker [poll] interrupted, stopping");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("FFmpeg Worker [poll] unexpected error: {}", e.getMessage());
                try {
                    Thread.sleep(pollInterval.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("FFmpeg Worker [poll] stopped");
    }

    private Record findQueuedJob() {
        try {
            List<Record> jobs = renderJobRepository.findQueuedJobs(1);
            return jobs.isEmpty() ? null : jobs.get(0);
        } catch (Exception e) {
            log.debug("No queued jobs found: {}", e.getMessage());
            return null;
        }
    }

    private boolean tryClaimJob(String jobId) {
        try {
            int updated = renderJobRepository.claimJob(jobId);
            return updated == 1;
        } catch (Exception e) {
            log.debug("Failed to claim job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private void markFailed(String jobId, String reason) {
        try {
            renderJobRepository.updateStatus(jobId, "FAILED");
        } catch (Exception e) {
            log.warn("Failed to mark job {} as FAILED: {}", jobId, e.getMessage());
        }
    }

    private Duration parseDuration(String s) {
        if (s == null || s.isBlank()) return Duration.ofSeconds(5);
        s = s.trim().toLowerCase();
        if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.replace("s", "")));
        if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.replace("m", "")));
        return Duration.ofSeconds(Long.parseLong(s));
    }
}
