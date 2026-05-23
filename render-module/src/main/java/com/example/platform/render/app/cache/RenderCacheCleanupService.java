package com.example.platform.render.app.cache;

import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Removes remote render-cache blobs for jobs older than {@link RenderCacheProperties#getRetentionDays()}.
 */
@Service
public class RenderCacheCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RenderCacheCleanupService.class);

    private final DSLContext dsl;
    private final PipelinePlanPersistenceService planPersistence;
    private final BlobStorage blobStorage;
    private final RenderCacheProperties cacheProperties;
    private final RenderCacheTenantGuard tenantGuard;

    public RenderCacheCleanupService(DSLContext dsl,
                                     PipelinePlanPersistenceService planPersistence,
                                     BlobStorage blobStorage,
                                     RenderCacheProperties cacheProperties,
                                     RenderCacheTenantGuard tenantGuard) {
        this.dsl = dsl;
        this.planPersistence = planPersistence;
        this.blobStorage = blobStorage;
        this.cacheProperties = cacheProperties;
        this.tenantGuard = tenantGuard;
    }

    public record CleanupResult(int jobsScanned, int objectsDeleted, int jobsUpdated) {}

    public CleanupResult runCleanup(String tenantId, String projectId) {
        if (!cacheProperties.isCleanupEnabled()) {
            return new CleanupResult(0, 0, 0);
        }
        int retentionDays = Math.max(1, cacheProperties.getRetentionDays());
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);

        Condition condition = field("status").eq("COMPLETED")
                .and(field("created_at").lessThan(cutoff));
        if (tenantId != null && !tenantId.isBlank()) {
            condition = condition.and(field("tenant_id").eq(tenantId));
        }
        if (projectId != null && !projectId.isBlank()) {
            condition = condition.and(field("project_id").eq(projectId));
        }

        var jobs = dsl.select(
                        field("id", String.class),
                        field("tenant_id", String.class),
                        field("project_id", String.class))
                .from(table("render_job"))
                .where(condition)
                .limit(500)
                .fetch();
        int deletedObjects = 0;
        int updatedJobs = 0;
        for (Record job : jobs) {
            String jobId = job.get(field("id", String.class));
            String jobTenant = job.get(field("tenant_id", String.class));
            try {
                int removed = cleanupJob(jobId, jobTenant);
                if (removed > 0) {
                    deletedObjects += removed;
                    updatedJobs++;
                }
            } catch (Exception e) {
                log.warn("Cache cleanup skipped for job {}: {}", jobId, e.getMessage());
            }
        }
        log.info("Render cache cleanup tenant={} project={} jobs={} deletedObjects={} updatedJobs={}",
                tenantId, projectId, jobs.size(), deletedObjects, updatedJobs);
        return new CleanupResult(jobs.size(), deletedObjects, updatedJobs);
    }

    @SuppressWarnings("unchecked")
    private int cleanupJob(String jobId, String tenantId) {
        Map<String, Object> state = planPersistence.loadExecutionState(jobId).orElse(null);
        if (state == null || state.isEmpty()) {
            return 0;
        }
        tenantGuard.assertExecutionStateTenant(tenantId, state);
        Set<String> uris = collectRemoteUris(state);
        int deleted = 0;
        for (String uri : uris) {
            tenantGuard.assertRemoteUriTenantPrefix(tenantId, uri);
            if (blobStorage.deleteStorageUri(uri)) {
                deleted++;
            }
        }
        if (deleted == 0) {
            return 0;
        }
        Map<String, Object> stripped = new LinkedHashMap<>(state);
        stripped.remove("segmentCacheIndex");
        stripped.remove("mezzanineCacheIndex");
        stripped.put("cacheCleanedAt", OffsetDateTime.now().toString());
        stripped.put("cacheObjectsRemoved", deleted);
        planPersistence.saveExecutionState(jobId, stripped);
        return deleted;
    }

    @SuppressWarnings("unchecked")
    static Set<String> collectRemoteUris(Map<String, Object> state) {
        Set<String> uris = new LinkedHashSet<>();
        Object segmentIndex = state.get("segmentCacheIndex");
        if (segmentIndex instanceof Map<?, ?> index) {
            index.values().forEach(entry -> addRemoteUri(uris, entry));
        }
        Object mezzanine = state.get("mezzanineCacheIndex");
        if (mezzanine instanceof Map<?, ?> entry) {
            addRemoteUri(uris, entry);
        }
        return uris;
    }

    private static void addRemoteUri(Set<String> uris, Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            return;
        }
        Object remote = map.get("remoteUri");
        if (remote != null && !String.valueOf(remote).isBlank()) {
            uris.add(String.valueOf(remote));
        }
    }
}
