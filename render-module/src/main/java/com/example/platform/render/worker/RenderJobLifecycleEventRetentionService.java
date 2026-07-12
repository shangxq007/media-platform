package com.example.platform.render.worker;

import com.example.platform.render.infrastructure.RenderJobLifecycleEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Retention and cleanup service for RenderJob lifecycle events.
 * 
 * Prevents unbounded growth of render_job_lifecycle_events table.
 */
@Service
public class RenderJobLifecycleEventRetentionService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobLifecycleEventRetentionService.class);

    private final RenderJobLifecycleEventRepository eventRepository;

    public RenderJobLifecycleEventRetentionService(RenderJobLifecycleEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Get retention summary without deleting anything.
     */
    public Map<String, Object> getRetentionSummary(Duration maxAge, int keepLatestPerJob) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(maxAge);

        int candidateCount = eventRepository.countEventsOlderThan(cutoff);
        Instant oldestEvent = eventRepository.findOldestEventTime();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", true);
        summary.put("dryRun", true);
        summary.put("cutoff", cutoff.toString());
        summary.put("maxAge", maxAge.toString());
        summary.put("keepLatestPerJob", keepLatestPerJob);
        summary.put("candidateCount", candidateCount);
        summary.put("oldestEvent", oldestEvent != null ? oldestEvent.toString() : null);
        summary.put("generatedAt", now.toString());
        return summary;
    }

    /**
     * Run cleanup in batches.
     * 
     * @param maxAge       maximum event age
     * @param batchSize    batch size for deletion
     * @param maxBatches   max batches to run (0 = unlimited)
     * @param dryRun       if true, only count candidates
     * @return summary of cleanup operation
     */
    public Map<String, Object> cleanup(Duration maxAge, int batchSize, int maxBatches, boolean dryRun) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(maxAge);

        log.info("Event retention cleanup: cutoff={} dryRun={} batchSize={} maxBatches={}",
                cutoff, dryRun, batchSize, maxBatches);

        int candidateCount = eventRepository.countEventsOlderThan(cutoff);
        int deletedTotal = 0;
        int batchesRun = 0;

        if (!dryRun) {
            while (maxBatches == 0 || batchesRun < maxBatches) {
                int deleted = eventRepository.deleteEventsOlderThan(cutoff, batchSize);
                deletedTotal += deleted;
                batchesRun++;
                if (deleted < batchSize) break; // No more candidates
            }
        }

        Instant oldestAfter = eventRepository.findOldestEventTime();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", true);
        summary.put("dryRun", dryRun);
        summary.put("cutoff", cutoff.toString());
        summary.put("maxAge", maxAge.toString());
        summary.put("batchSize", batchSize);
        summary.put("candidateCount", candidateCount);
        summary.put("deletedCount", deletedTotal);
        summary.put("batchesRun", batchesRun);
        summary.put("oldestEventBefore", candidateCount > 0 ? cutoff.toString() : null);
        summary.put("oldestEventAfter", oldestAfter != null ? oldestAfter.toString() : null);
        summary.put("completedAt", Instant.now().toString());

        log.info("Event retention cleanup complete: candidates={} deleted={} batches={} dryRun={}",
                candidateCount, deletedTotal, batchesRun, dryRun);

        return summary;
    }
}
