package com.example.platform.render.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-process Natron worker queue (Phase 3 POC). Production would use Temporal / Redis / SQS.
 */
@Service
public class RenderWorkerQueueService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerQueueService.class);
    public static final String WORKER_TYPE_NATRON = "natron";
    public static final String WORKER_TYPE_REMOTION = "remotion";
    public static final String WORKER_TYPE_BLENDER = "blender";

    private final RenderWorkerQueueProperties properties;
    private final ConcurrentLinkedQueue<RenderWorkerQueueJob> natronQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RenderWorkerQueueJob> remotionQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RenderWorkerQueueJob> blenderQueue = new ConcurrentLinkedQueue<>();

    public RenderWorkerQueueService(RenderWorkerQueueProperties properties) {
        this.properties = properties;
    }

    public boolean enqueueNatron(String jobId, String tenantId, String profile) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (natronQueue.size() >= properties.getMaxNatronDepth()) {
            log.warn("Natron worker queue full (max={}), job {} not queued", properties.getMaxNatronDepth(), jobId);
            return false;
        }
        RenderWorkerQueueJob job = new RenderWorkerQueueJob(jobId, tenantId, profile, WORKER_TYPE_NATRON, Instant.now());
        natronQueue.offer(job);
        log.info("Enqueued Natron worker job {} profile={} depth={}", jobId, profile, natronQueue.size());
        return true;
    }

    public Optional<RenderWorkerQueueJob> pollNatron() {
        return Optional.ofNullable(natronQueue.poll());
    }

    public List<RenderWorkerQueueJob> snapshotNatronQueue() {
        return new ArrayList<>(natronQueue);
    }

    public int natronDepth() {
        return natronQueue.size();
    }

    public boolean enqueueRemotion(String jobId, String tenantId, String profile) {
        return enqueue(remotionQueue, WORKER_TYPE_REMOTION, jobId, tenantId, profile);
    }

    public boolean enqueueBlender(String jobId, String tenantId, String profile) {
        return enqueue(blenderQueue, WORKER_TYPE_BLENDER, jobId, tenantId, profile);
    }

    public Optional<RenderWorkerQueueJob> pollRemotion() {
        return Optional.ofNullable(remotionQueue.poll());
    }

    public Optional<RenderWorkerQueueJob> pollBlender() {
        return Optional.ofNullable(blenderQueue.poll());
    }

    public int remotionDepth() {
        return remotionQueue.size();
    }

    public int blenderDepth() {
        return blenderQueue.size();
    }

    private boolean enqueue(ConcurrentLinkedQueue<RenderWorkerQueueJob> queue, String workerType,
                            String jobId, String tenantId, String profile) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (queue.size() >= properties.getMaxNatronDepth()) {
            log.warn("{} worker queue full, job {} not queued", workerType, jobId);
            return false;
        }
        queue.offer(new RenderWorkerQueueJob(jobId, tenantId, profile, workerType, Instant.now()));
        log.info("Enqueued {} worker job {} depth={}", workerType, jobId, queue.size());
        return true;
    }
}
