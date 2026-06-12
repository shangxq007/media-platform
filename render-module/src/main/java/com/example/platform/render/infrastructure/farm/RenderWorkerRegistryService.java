package com.example.platform.render.infrastructure.farm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages the render worker registry: registration, heartbeat, status transitions.
 */
@Service
public class RenderWorkerRegistryService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerRegistryService.class);

    private final RenderWorkerRepository workerRepository;

    public RenderWorkerRegistryService(RenderWorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    /**
     * Register a new worker or re-register an existing one.
     * After registration, the worker is in STARTING status.
     */
    public void registerWorker(RenderWorkerRegistration registration) {
        Instant now = Instant.now();
        workerRepository.register(registration, now);
        log.info("Worker registered: {} (type={}, providers={})", registration.workerId(),
                registration.workerType(), registration.providerIds());
    }

    /**
     * Process a heartbeat from a worker. Updates status, resources, and last heartbeat time.
     * Does NOT transition DRAINING workers back to IDLE.
     *
     * @return true if heartbeat was accepted
     */
    public boolean heartbeat(RenderWorkerHeartbeat hb) {
        Instant now = Instant.now();

        // Check if worker exists
        var existing = workerRepository.findByWorkerId(hb.workerId());
        if (existing.isEmpty()) {
            log.warn("Heartbeat from unknown worker: {}", hb.workerId());
            return false;
        }

        var worker = existing.get();

        // Don't let heartbeat bring DRAINING workers back to IDLE
        if (worker.status() == RenderWorkerStatus.DRAINING && hb.status() == RenderWorkerStatus.IDLE) {
            // Force to BUSY if draining and has active jobs, keep DRAINING otherwise
            if (worker.activeJobCount() != null && worker.activeJobCount() > 0) {
                hb = new RenderWorkerHeartbeat(hb.workerId(), RenderWorkerStatus.DRAINING,
                        hb.activeJobCount(), hb.cpuCores(), hb.memoryMb(),
                        hb.gpuCount(), hb.gpuType(), hb.diskFreeMb(), hb.metadataJson());
            }
            // If no active jobs, transition to IDLE (drain complete)
        }

        boolean accepted = workerRepository.heartbeat(hb, now);
        if (accepted) {
            log.debug("Heartbeat accepted: {} (status={}, activeJobs={})", hb.workerId(),
                    hb.status(), hb.activeJobCount());
        }
        return accepted;
    }

    /**
     * Transition a worker to IDLE (ready for jobs).
     */
    public void markIdle(String workerId) {
        workerRepository.markIdle(workerId, Instant.now());
        log.info("Worker marked IDLE: {}", workerId);
    }

    /**
     * Transition a worker to DRAINING (finishing current jobs, not accepting new ones).
     */
    public void markDraining(String workerId) {
        workerRepository.markDraining(workerId, Instant.now());
        log.info("Worker marked DRAINING: {}", workerId);
    }

    /**
     * Transition a worker to OFFLINE.
     */
    public void markOffline(String workerId) {
        workerRepository.markOffline(workerId, Instant.now());
        log.info("Worker marked OFFLINE: {}", workerId);
    }

    /**
     * Transition a worker to FAILED.
     */
    public void markFailed(String workerId) {
        workerRepository.markFailed(workerId, Instant.now());
        log.info("Worker marked FAILED: {}", workerId);
    }

    /**
     * Find workers that can accept new jobs (IDLE or BUSY with capacity).
     */
    public List<RenderWorkerRecord> findAvailableWorkers() {
        return workerRepository.findAvailableWorkers().stream()
                .filter(RenderWorkerRecord::canAcceptJobs)
                .toList();
    }

    /**
     * Prune workers that have not sent a heartbeat within the given duration.
     * Marks them as OFFLINE.
     */
    public int pruneStaleWorkers(Duration heartbeatTimeout) {
        Instant threshold = Instant.now().minus(heartbeatTimeout);
        List<RenderWorkerRecord> stale = workerRepository.findStaleWorkers(threshold);
        for (RenderWorkerRecord worker : stale) {
            workerRepository.markOffline(worker.workerId(), Instant.now());
            log.warn("Pruned stale worker: {} (lastHeartbeat={})", worker.workerId(), worker.lastHeartbeatAt());
        }
        return stale.size();
    }

    /**
     * Increment active job count for a worker (called when a lease is claimed).
     */
    public void incrementActiveJobs(String workerId) {
        workerRepository.incrementActiveJobs(workerId);
    }

    /**
     * Decrement active job count for a worker (called when a lease is released/failed/expired).
     */
    public void decrementActiveJobs(String workerId) {
        workerRepository.decrementActiveJobs(workerId);
    }

    /**
     * List all workers.
     */
    public List<RenderWorkerRecord> listWorkers() {
        return workerRepository.listAll();
    }

    /**
     * Find a worker by ID.
     */
    public RenderWorkerRecord getWorker(String workerId) {
        return workerRepository.findByWorkerId(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found: " + workerId));
    }
}
