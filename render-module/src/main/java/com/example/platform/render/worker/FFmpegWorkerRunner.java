package com.example.platform.render.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal FFmpeg render worker that executes a single RenderJob by ID.
 * 
 * Usage:
 *   java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
 *        --render.worker.job-id=rj_xxx
 */
@Component
@ConditionalOnProperty(name = "render.worker.enabled", havingValue = "true")
public class FFmpegWorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FFmpegWorkerRunner.class);

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
            // TODO: Implement direct RenderJob execution
            // For now, log the worker boundary
            log.info("FFmpeg Worker boundary established. Job: {}", jobId);
            log.info("Worker would execute: materialize inputs → FFmpeg → store output");
            log.info("This is a design placeholder. Full implementation requires RenderJob execution service extraction.");
            
            System.exit(0);
        } catch (Exception e) {
            log.error("FFmpeg Worker failed for job {}: {}", jobId, e.getMessage(), e);
            System.exit(1);
        }
    }
}
