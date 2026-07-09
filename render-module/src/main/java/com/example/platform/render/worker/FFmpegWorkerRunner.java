package com.example.platform.render.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * CLI once FFmpeg render worker that executes a single RenderJob by ID.
 * 
 * Usage:
 *   java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
 *        --render.worker.job-id=rj_xxx
 */
@Component
@ConditionalOnProperty(name = "render.worker.enabled", havingValue = "true")
public class FFmpegWorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FFmpegWorkerRunner.class);

    private final RenderWorkerExecutionService workerExecutionService;

    public FFmpegWorkerRunner(RenderWorkerExecutionService workerExecutionService) {
        this.workerExecutionService = workerExecutionService;
    }

    @Override
    public void run(String... args) throws Exception {
        String jobId = System.getProperty("render.worker.job-id",
                System.getenv().getOrDefault("RENDER_WORKER_JOB_ID", ""));
        
        if (jobId == null || jobId.isBlank()) {
            log.error("No job ID specified. Use --render.worker.job-id=rj_xxx or RENDER_WORKER_JOB_ID env var");
            System.exit(1);
        }

        log.info("FFmpeg Worker starting for job: {}", jobId);
        
        try {
            String resultJobId = workerExecutionService.executeOnce(jobId);
            log.info("FFmpeg Worker completed successfully: jobId={}", resultJobId);
            System.exit(0);
        } catch (Exception e) {
            log.error("FFmpeg Worker failed for job {}: {}", jobId, e.getMessage());
            System.exit(1);
        }
    }
}
