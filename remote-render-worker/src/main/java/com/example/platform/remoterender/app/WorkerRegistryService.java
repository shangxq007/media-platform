package com.example.platform.remoterender.app;

import com.example.platform.remoterender.domain.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of remote render workers.
 */
@Service
public class WorkerRegistryService {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistryService.class);
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();

    public String registerWorker(String workerAddress, int maxConcurrentJobs) {
        String workerId = UUID.randomUUID().toString().substring(0, 8);
        workers.put(workerId, new WorkerInfo(workerId, workerAddress, WorkerStatus.IDLE,
                maxConcurrentJobs, 0, Instant.now(), Instant.now()));
        log.info("Worker registered: id={}, address={}", workerId, workerAddress);
        return workerId;
    }

    public void deregisterWorker(String workerId) {
        workers.remove(workerId);
        log.info("Worker deregistered: id={}", workerId);
    }

    public void updateWorkerStatus(String workerId, WorkerStatus status) {
        WorkerInfo info = workers.get(workerId);
        if (info != null) {
            workers.put(workerId, info.withStatus(status));
        }
    }

    public void heartbeat(String workerId) {
        WorkerInfo info = workers.get(workerId);
        if (info != null) {
            workers.put(workerId, info.withLastHeartbeat(Instant.now()));
        }
    }

    public Map<String, WorkerInfo> getAllWorkers() {
        return Map.copyOf(workers);
    }

    public WorkerInfo getWorker(String workerId) {
        return workers.get(workerId);
    }

    /**
     * Information about a registered worker.
     */
    public record WorkerInfo(
            String workerId,
            String workerAddress,
            WorkerStatus status,
            int maxConcurrentJobs,
            int activeJobs,
            Instant lastHeartbeat,
            Instant registeredAt
    ) {
        public WorkerInfo withStatus(WorkerStatus newStatus) {
            return new WorkerInfo(workerId, workerAddress, newStatus, maxConcurrentJobs,
                    activeJobs, lastHeartbeat, registeredAt);
        }

        public WorkerInfo withLastHeartbeat(Instant heartbeat) {
            return new WorkerInfo(workerId, workerAddress, status, maxConcurrentJobs,
                    activeJobs, heartbeat, registeredAt);
        }

        public boolean isAvailable() {
            return status == WorkerStatus.IDLE && activeJobs < maxConcurrentJobs;
        }
    }
}
