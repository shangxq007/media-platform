package com.example.platform.render.infrastructure.farm.api;

import com.example.platform.render.infrastructure.farm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal API for render farm workers.
 *
 * <p>These endpoints are used by remote render workers to register, heartbeat,
 * claim jobs, and report completion/failure. They are NOT tenant-facing public APIs.
 *
 * <p>Security: Worker authentication should be added (API key or mTLS).
 * Currently relies on network-level isolation.
 */
@RestController
@RequestMapping("/internal/render-workers")
public class RenderFarmWorkerController {

    private static final Logger log = LoggerFactory.getLogger(RenderFarmWorkerController.class);

    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int LEASE_DURATION_SECONDS = 600;

    private final RenderWorkerRegistryService workerRegistry;
    private final RenderJobLeaseService leaseService;

    public RenderFarmWorkerController(RenderWorkerRegistryService workerRegistry,
            RenderJobLeaseService leaseService) {
        this.workerRegistry = workerRegistry;
        this.leaseService = leaseService;
    }

    /**
     * Register a new render worker or re-register an existing one.
     */
    @PostMapping("/register")
    public ResponseEntity<WorkerRegisterResponse> register(@RequestBody WorkerRegisterRequest request) {
        log.info("Worker registration: workerId={}, type={}, providers={}",
                request.workerId(), request.workerType(), request.providerIds());

        RenderWorkerRegistration registration = new RenderWorkerRegistration(
                request.workerId(),
                request.workerType(),
                request.version(),
                request.imageTag(),
                request.hostname(),
                request.zone(),
                request.providerIds() != null ? String.join(",", request.providerIds()) : null,
                request.capabilitiesJson(),
                request.maxConcurrentJobs(),
                request.cpuCores(),
                request.memoryMb(),
                request.gpuCount(),
                request.gpuType(),
                request.diskFreeMb()
        );

        workerRegistry.registerWorker(registration);

        return ResponseEntity.ok(new WorkerRegisterResponse(
                request.workerId(),
                HEARTBEAT_INTERVAL_SECONDS,
                LEASE_DURATION_SECONDS,
                "REGISTERED"
        ));
    }

    /**
     * Worker heartbeat — updates status, resources, and last heartbeat time.
     */
    @PostMapping("/{workerId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @PathVariable String workerId,
            @RequestBody WorkerHeartbeatRequest request) {
        RenderWorkerHeartbeat hb = new RenderWorkerHeartbeat(
                workerId,
                RenderWorkerStatus.valueOf(request.status()),
                request.activeJobCount(),
                request.cpuCores(),
                request.memoryMb(),
                request.gpuCount(),
                request.gpuType(),
                request.diskFreeMb(),
                request.metadataJson()
        );

        boolean accepted = workerRegistry.heartbeat(hb);

        if (!accepted) {
            return ResponseEntity.badRequest().body(Map.of(
                    "accepted", false,
                    "reason", "Heartbeat rejected — worker may be OFFLINE or unknown"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accepted", true,
                "workerId", workerId,
                "heartbeatIntervalSeconds", HEARTBEAT_INTERVAL_SECONDS
        ));
    }

    /**
     * Claim the next available job for this worker.
     */
    @PostMapping("/{workerId}/claim")
    public ResponseEntity<WorkerClaimResponse> claim(
            @PathVariable String workerId,
            @RequestBody WorkerClaimRequest request) {
        log.info("Worker claim request: workerId={}, providers={}, allowPoc={}, mode={}",
                workerId, request.providerIds(), request.allowPoc(), request.mode());

        RenderFarmClaimResult result = leaseService.claimNextJob(
                workerId, request.providerIds(), request.allowPoc(), request.mode());

        if (!result.isClaimed()) {
            return ResponseEntity.ok(WorkerClaimResponse.noJob());
        }

        return ResponseEntity.ok(WorkerClaimResponse.success(
                result.leaseId(),
                result.jobId(),
                result.tenantId(),
                result.providerId(),
                result.attempt(),
                result.maxAttempts(),
                result.leaseUntil(),
                result.renderProfile(),
                result.timelineJson()
        ));
    }

    /**
     * Renew a lease — extends the lease window.
     */
    @PostMapping("/{workerId}/leases/{leaseId}/renew")
    public ResponseEntity<Map<String, Object>> renewLease(
            @PathVariable String workerId,
            @PathVariable String leaseId) {
        boolean renewed = leaseService.renewLease(leaseId, workerId);

        if (!renewed) {
            return ResponseEntity.badRequest().body(Map.of(
                    "renewed", false,
                    "reason", "Renew failed — lease not found, wrong worker, or version conflict"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "renewed", true,
                "leaseId", leaseId
        ));
    }

    /**
     * Complete a lease — job finished successfully with artifact.
     */
    @PostMapping("/{workerId}/leases/{leaseId}/complete")
    public ResponseEntity<Map<String, Object>> completeLease(
            @PathVariable String workerId,
            @PathVariable String leaseId,
            @RequestBody WorkerCompleteRequest request) {
        log.info("Worker completion: workerId={}, leaseId={}, jobId={}, artifactUri={}",
                workerId, leaseId, request.jobId(), request.artifactUri());

        LeaseReleaseResult result = leaseService.completeLease(
                leaseId, workerId, request.artifactUri(),
                request.checksum(), request.durationMs());

        if (!result.isReleased()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "completed", false,
                    "reason", result.failureReason()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "completed", true,
                "leaseId", leaseId,
                "jobId", result.jobId()
        ));
    }

    /**
     * Fail a lease — job execution failed.
     */
    @PostMapping("/{workerId}/leases/{leaseId}/fail")
    public ResponseEntity<Map<String, Object>> failLease(
            @PathVariable String workerId,
            @PathVariable String leaseId,
            @RequestBody WorkerFailRequest request) {
        log.info("Worker failure: workerId={}, leaseId={}, jobId={}, errorCode={}, retryable={}",
                workerId, leaseId, request.jobId(), request.errorCode(), request.retryable());

        boolean failed = leaseService.failLease(
                leaseId, workerId, request.errorMessage(),
                request.errorCode(), request.retryable());

        if (!failed) {
            return ResponseEntity.badRequest().body(Map.of(
                    "failed", false,
                    "reason", "Fail failed — lease not found or wrong worker"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "failed", true,
                "leaseId", leaseId,
                "retryable", request.retryable()
        ));
    }

    /**
     * List all registered workers (admin/debug).
     */
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> listWorkers() {
        List<RenderWorkerRecord> workers = workerRegistry.listWorkers();
        return ResponseEntity.ok(Map.of(
                "workers", workers,
                "count", workers.size()
        ));
    }
}
