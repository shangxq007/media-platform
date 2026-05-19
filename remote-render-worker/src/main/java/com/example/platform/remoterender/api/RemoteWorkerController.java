package com.example.platform.remoterender.api;

import com.example.platform.remoterender.app.RemoteRenderService;
import com.example.platform.remoterender.app.WorkerRegistryService;
import com.example.platform.remoterender.domain.RemoteRenderJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for remote render worker operations.
 */
@RestController
@RequestMapping("/api/v1/remote-worker")
public class RemoteWorkerController {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkerController.class);

    private final RemoteRenderService renderService;
    private final WorkerRegistryService workerRegistry;

    public RemoteWorkerController(RemoteRenderService renderService, WorkerRegistryService workerRegistry) {
        this.renderService = renderService;
        this.workerRegistry = workerRegistry;
    }

    /**
     * Register a new worker.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerWorker(
            @RequestBody Map<String, Object> request) {
        String address = (String) request.getOrDefault("address", "unknown");
        int maxJobs = (int) request.getOrDefault("maxConcurrentJobs", 4);
        String workerId = workerRegistry.registerWorker(address, maxJobs);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "REGISTERED"));
    }

    /**
     * Deregister a worker.
     */
    @PostMapping("/deregister/{workerId}")
    public ResponseEntity<Map<String, String>> deregisterWorker(@PathVariable String workerId) {
        workerRegistry.deregisterWorker(workerId);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "DEREGISTERED"));
    }

    /**
     * Worker heartbeat.
     */
    @PostMapping("/heartbeat/{workerId}")
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable String workerId) {
        workerRegistry.heartbeat(workerId);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "ALIVE"));
    }

    /**
     * Get all registered workers.
     */
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> listWorkers() {
        return ResponseEntity.ok(Map.of(
                "workers", workerRegistry.getAllWorkers(),
                "count", workerRegistry.getAllWorkers().size()
        ));
    }

    /**
     * Get worker status.
     */
    @GetMapping("/workers/{workerId}")
    public ResponseEntity<?> getWorkerStatus(@PathVariable String workerId) {
        WorkerRegistryService.WorkerInfo info = workerRegistry.getWorker(workerId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }

    /**
     * Submit a render job to this worker.
     */
    @PostMapping("/workers/{workerId}/jobs")
    public ResponseEntity<RemoteRenderJob> submitJob(
            @PathVariable String workerId,
            @RequestBody Map<String, Object> request) {
        String profile = (String) request.getOrDefault("profile", "default_1080p");
        String timelineJson = (String) request.getOrDefault("timelineJson", "{}");

        RemoteRenderJob job = renderService.submitJob(workerId, profile, timelineJson);
        log.info("RemoteWorkerController: job submitted: {} to worker: {}", job.jobId(), workerId);
        return ResponseEntity.ok(job);
    }

    /**
     * Get job status.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<RemoteRenderJob> getJobStatus(@PathVariable String jobId) {
        RemoteRenderJob job = renderService.getJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    /**
     * Get all jobs for a worker.
     */
    @GetMapping("/workers/{workerId}/jobs")
    public ResponseEntity<List<RemoteRenderJob>> getWorkerJobs(@PathVariable String workerId) {
        return ResponseEntity.ok(renderService.getWorkerJobs(workerId));
    }

    /**
     * Cancel a job.
     */
    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<RemoteRenderJob> cancelJob(@PathVariable String jobId) {
        RemoteRenderJob job = renderService.cancelJob(jobId);
        return ResponseEntity.ok(job);
    }
}
