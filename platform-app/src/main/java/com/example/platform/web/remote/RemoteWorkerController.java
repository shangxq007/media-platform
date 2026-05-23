package com.example.platform.web.remote;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/remote-worker")
@Tag(name = "Remote Worker", description = "Remote render worker management and job distribution")
public class RemoteWorkerController {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkerController.class);
    private final Map<String, Map<String, Object>> workers = new ConcurrentHashMap<>();

    @GetMapping("/workers")
    @Operation(summary = "List available remote workers",
               description = "Returns all registered remote render workers with their current status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved worker list")
    })
    public ResponseEntity<Map<String, Object>> listWorkers() {
        return ResponseEntity.ok(Map.of(
                "workers", workers.values(),
                "count", workers.size()
        ));
    }

    @GetMapping("/workers/{workerId}")
    @Operation(summary = "Get worker status",
               description = "Retrieve status and details of a specific remote worker")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved worker status"),
        @ApiResponse(responseCode = "404", description = "Worker not found")
    })
    public ResponseEntity<?> getWorkerStatus(@PathVariable String workerId) {
        Map<String, Object> worker = workers.get(workerId);
        if (worker == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(worker);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a remote worker",
               description = "Register a new remote render worker with the platform")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Worker registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid registration request")
    })
    public ResponseEntity<Map<String, String>> registerWorker(@RequestBody Map<String, Object> request) {
        String address = (String) request.getOrDefault("address", "unknown");
        int maxJobs = (int) request.getOrDefault("maxConcurrentJobs", 4);
        String workerId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> workerInfo = new LinkedHashMap<>();
        workerInfo.put("workerId", workerId);
        workerInfo.put("address", address);
        workerInfo.put("status", "IDLE");
        workerInfo.put("maxConcurrentJobs", maxJobs);
        workerInfo.put("activeJobs", 0);
        workerInfo.put("lastHeartbeat", Instant.now().toString());
        workerInfo.put("registeredAt", Instant.now().toString());
        workers.put(workerId, workerInfo);

        log.info("Worker registered: id={}, address={}", workerId, address);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "REGISTERED"));
    }

    @PostMapping("/deregister/{workerId}")
    @Operation(summary = "Deregister a remote worker",
               description = "Remove a remote render worker from the platform")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Worker deregistered successfully"),
        @ApiResponse(responseCode = "404", description = "Worker not found")
    })
    public ResponseEntity<Map<String, String>> deregisterWorker(@PathVariable String workerId) {
        if (!workers.containsKey(workerId)) {
            return ResponseEntity.notFound().build();
        }
        workers.remove(workerId);
        log.info("Worker deregistered: id={}", workerId);
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "DEREGISTERED"));
    }

    @PostMapping("/heartbeat/{workerId}")
    @Operation(summary = "Worker heartbeat",
               description = "Send a heartbeat signal from a remote worker to confirm it is alive")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Heartbeat acknowledged"),
        @ApiResponse(responseCode = "404", description = "Worker not found")
    })
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable String workerId) {
        Map<String, Object> worker = workers.get(workerId);
        if (worker == null) {
            return ResponseEntity.notFound().build();
        }
        worker.put("lastHeartbeat", Instant.now().toString());
        return ResponseEntity.ok(Map.of("workerId", workerId, "status", "ALIVE"));
    }
}
