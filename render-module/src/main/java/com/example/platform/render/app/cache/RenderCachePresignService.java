package com.example.platform.render.app.cache;

import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Issues presigned download URLs for segment / mezzanine cache entries on a completed render job.
 */
@Service
public class RenderCachePresignService {

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final RenderCacheTenantGuard tenantGuard;
    private final PipelinePlanPersistenceService planPersistence;
    private final BlobStorage blobStorage;
    private final RenderCacheProperties cacheProperties;

    public RenderCachePresignService(RenderCacheTenantGuard tenantGuard,
                                     PipelinePlanPersistenceService planPersistence,
                                     BlobStorage blobStorage,
                                     RenderCacheProperties cacheProperties) {
        this.tenantGuard = tenantGuard;
        this.planPersistence = planPersistence;
        this.blobStorage = blobStorage;
        this.cacheProperties = cacheProperties;
    }

    public record CacheEntryPresign(
            String cacheKey,
            String segmentId,
            String taskId,
            String kind,
            String sourceUri,
            String downloadUrl,
            Duration expiresIn) {}

    public record CachePresignResponse(String jobId, List<CacheEntryPresign> entries) {}

    public CachePresignResponse presignAll(String tenantId, String projectId, String jobId) {
        tenantGuard.requireJobAccess(tenantId, projectId, jobId);
        Map<String, Object> state = planPersistence.loadExecutionState(jobId).orElse(Map.of());
        tenantGuard.assertExecutionStateTenant(tenantId, state);
        List<CacheEntryPresign> entries = new ArrayList<>();
        collectSegmentEntries(tenantId, state, entries);
        collectMezzanineEntry(tenantId, state, entries);
        return new CachePresignResponse(jobId, List.copyOf(entries));
    }

    public CacheEntryPresign presignOne(String tenantId, String projectId, String jobId, String cacheKey) {
        tenantGuard.requireJobAccess(tenantId, projectId, jobId);
        Map<String, Object> state = planPersistence.loadExecutionState(jobId).orElse(Map.of());
        tenantGuard.assertExecutionStateTenant(tenantId, state);
        return findEntry(tenantId, state, cacheKey)
                .orElseThrow(() -> new IllegalArgumentException("Cache entry not found: " + cacheKey));
    }

    @SuppressWarnings("unchecked")
    private void collectSegmentEntries(String tenantId, Map<String, Object> state,
                                       List<CacheEntryPresign> entries) {
        Object indexObj = state.get("segmentCacheIndex");
        if (!(indexObj instanceof Map<?, ?> index)) {
            return;
        }
        index.forEach((key, value) -> {
            if (value instanceof Map<?, ?> entry) {
                presignEntry(tenantId, String.valueOf(key), "segment", entry).ifPresent(entries::add);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void collectMezzanineEntry(String tenantId, Map<String, Object> state,
                                       List<CacheEntryPresign> entries) {
        Object mezzanine = state.get("mezzanineCacheIndex");
        if (!(mezzanine instanceof Map<?, ?> entry)) {
            return;
        }
        String cacheKey = stringVal(entry.get("cacheKey"), "final_compose");
        presignEntry(tenantId, cacheKey, "mezzanine", entry).ifPresent(entries::add);
    }

    @SuppressWarnings("unchecked")
    private Optional<CacheEntryPresign> findEntry(String tenantId, Map<String, Object> state, String cacheKey) {
        Object indexObj = state.get("segmentCacheIndex");
        if (indexObj instanceof Map<?, ?> index && index.containsKey(cacheKey)) {
            Object entry = index.get(cacheKey);
            if (entry instanceof Map<?, ?> map) {
                return presignEntry(tenantId, cacheKey, "segment", map);
            }
        }
        Object mezzanine = state.get("mezzanineCacheIndex");
        if (mezzanine instanceof Map<?, ?> entry) {
            String mezzanineKey = stringVal(entry.get("cacheKey"), "final_compose");
            if (cacheKey.equals(mezzanineKey) || "final_compose".equals(cacheKey)) {
                return presignEntry(tenantId, mezzanineKey, "mezzanine", entry);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<CacheEntryPresign> presignEntry(String tenantId, String cacheKey, String kind,
                                                     Map<?, ?> entry) {
        String segmentId = stringVal(entry.get("segmentId"), "");
        String taskId = stringVal(entry.get("taskId"), segmentId.isBlank() ? "final_compose" : segmentId);
        String remoteUri = stringVal(entry.get("remoteUri"), "");
        String localUri = stringVal(entry.get("uri"), "");
        String sourceUri = !remoteUri.isBlank() ? remoteUri : localUri;
        if (sourceUri.isBlank()) {
            return Optional.empty();
        }
        if (!remoteUri.isBlank()) {
            tenantGuard.assertRemoteUriTenantPrefix(tenantId, remoteUri);
        }
        Optional<String> download = resolveDownloadUrl(tenantId, sourceUri);
        if (download.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CacheEntryPresign(
                cacheKey,
                segmentId.isBlank() ? null : segmentId,
                taskId,
                kind,
                sourceUri,
                download.get(),
                PRESIGN_TTL));
    }

    private Optional<String> resolveDownloadUrl(String tenantId, String uri) {
        if (uri == null || uri.isBlank()) {
            return Optional.empty();
        }
        Optional<String> storagePresign = BlobStorage.parseUri(uri)
                .flatMap(ref -> blobStorage.presignStorageUri(uri));
        if (storagePresign.isPresent()) {
            return storagePresign;
        }
        if (cacheProperties.isRemoteEnabled() && uri.startsWith("http")) {
            return Optional.of(uri);
        }
        return Optional.of(uri);
    }

    private static String stringVal(Object o, String defaultValue) {
        return o != null && !String.valueOf(o).isBlank() ? String.valueOf(o) : defaultValue;
    }
}
