package com.example.platform.web.remote;

import com.example.platform.render.infrastructure.remote.RemoteRenderDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/remote-worker")
public class RemoteWorkerController {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkerController.class);

    private final RemoteRenderDispatcher dispatcher;

    public RemoteWorkerController(RemoteRenderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> listWorkers() {
        List<RemoteRenderDispatcher.WorkerInfo> workers = dispatcher.listWorkers();
        return ResponseEntity.ok(Map.of(
                "workers", workers,
                "count", workers.size()
        ));
    }

    @GetMapping("/workers/{workerId}")
    public ResponseEntity<?> getWorker(@PathVariable String workerId) {
        return dispatcher.listWorkers().stream()
                .filter(w -> w.workerId().equals(workerId))
                .findFirst()
                .map(w -> ResponseEntity.ok((Object) w))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, Object> request) {
        String address = (String) request.getOrDefault("address", "unknown");
        int maxJobs = ((Number) request.getOrDefault("maxConcurrentJobs", 4)).intValue();
        String workerId = java.util.UUID.randomUUID().toString().substring(0, 8);

        dispatcher.registerWorker(workerId, address, maxJobs);

        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "REGISTERED"));
    }

    @PostMapping("/deregister/{workerId}")
    public ResponseEntity<Map<String, String>> deregister(@PathVariable String workerId) {
        dispatcher.deregisterWorker(workerId);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "DEREGISTERED"));
    }

    @PostMapping("/heartbeat/{workerId}")
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable String workerId) {
        dispatcher.heartbeat(workerId);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "ALIVE"));
    }

    @PostMapping("/jobs/{jobId}/callback")
    public ResponseEntity<Map<String, String>> jobCallback(
            @PathVariable String jobId,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "UNKNOWN");
        int progress = ((Number) body.getOrDefault("progress", 0)).intValue();
        String artifactId = (String) body.getOrDefault("artifactId", "");
        String storageUri = (String) body.getOrDefault("storageUri", "");
        String errorCode = (String) body.getOrDefault("errorCode", "");
        String errorMessage = (String) body.getOrDefault("errorMessage", "");

        dispatcher.handleCallback(jobId, status, progress, artifactId, storageUri, errorCode, errorMessage);

        return ResponseEntity.ok(Map.of("jobId", jobId, "status", "ACK"));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        return dispatcher.getJobStatus(jobId)
                .map(t -> ResponseEntity.ok((Object) t))
                .orElse(ResponseEntity.notFound().build());
    }
}
