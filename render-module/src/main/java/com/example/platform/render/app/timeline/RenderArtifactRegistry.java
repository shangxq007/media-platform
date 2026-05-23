package com.example.platform.render.app.timeline;

import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves reusable artifacts from explicit input or prior job execution state.
 */
@Service
public class RenderArtifactRegistry {

    private final PipelinePlanPersistenceService planPersistence;
    private final RenderCacheTenantGuard tenantGuard;

    public RenderArtifactRegistry(PipelinePlanPersistenceService planPersistence,
                                    RenderCacheTenantGuard tenantGuard) {
        this.planPersistence = planPersistence;
        this.tenantGuard = tenantGuard;
    }

    public List<ReusableArtifact> resolve(String baseJobId, List<ReusableArtifact> explicit) {
        return resolve(null, baseJobId, explicit);
    }

    public List<ReusableArtifact> resolve(String tenantId, String baseJobId, List<ReusableArtifact> explicit) {
        if (explicit != null && !explicit.isEmpty()) {
            return List.copyOf(explicit);
        }
        if (planPersistence == null || baseJobId == null || baseJobId.isBlank()) {
            return List.of();
        }
        if (tenantId != null && !tenantId.isBlank() && tenantGuard != null) {
            tenantGuard.requireJobTenant(tenantId, baseJobId);
        }
        return planPersistence.loadExecutionState(baseJobId)
                .map(state -> {
                    if (tenantId != null && !tenantId.isBlank() && tenantGuard != null) {
                        tenantGuard.assertExecutionStateTenant(tenantId, state);
                    }
                    return fromExecutionState(state);
                })
                .orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<ReusableArtifact> fromExecutionState(Map<String, Object> state) {
        List<ReusableArtifact> artifacts = new ArrayList<>();
        Object external = state.get("externalArtifacts");
        if (external instanceof Map<?, ?> map) {
            map.forEach((taskId, uri) -> {
                if (uri != null) {
                    artifacts.add(ReusableArtifact.of(String.valueOf(taskId), String.valueOf(uri), ""));
                }
            });
        }
        Object reuseList = state.get("reuseArtifacts");
        if (reuseList instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    artifacts.add(mapArtifact(m));
                }
            }
        }
        Object pipelineStages = state.get("pipelineStageArtifacts");
        if (pipelineStages instanceof Map<?, ?> stageMap) {
            mergeArtifactMap(artifacts, stageMap);
        }
        Object segmentArtifacts = state.get("segmentArtifacts");
        if (segmentArtifacts instanceof Map<?, ?> segmentMap) {
            mergeArtifactMap(artifacts, segmentMap);
        }
        Object segmentCacheIndex = state.get("segmentCacheIndex");
        if (segmentCacheIndex instanceof Map<?, ?> cacheIndex) {
            mergeSegmentCacheIndex(artifacts, cacheIndex);
        }
        Object mezzanineCacheIndex = state.get("mezzanineCacheIndex");
        if (mezzanineCacheIndex instanceof Map<?, ?> mezzanine) {
            mergeMezzanineCacheIndex(artifacts, mezzanine);
        }
        return artifacts;
    }

    public Map<String, String> loadContentHashes(String baseJobId) {
        if (planPersistence == null || baseJobId == null || baseJobId.isBlank()) {
            return Map.of();
        }
        return planPersistence.loadExecutionState(baseJobId)
                .map(this::contentHashesFromState)
                .orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> contentHashesFromState(Map<String, Object> state) {
        Map<String, String> hashes = new LinkedHashMap<>();
        collectHashes(hashes, state.get("segmentCacheIndex"));
        Object mezzanine = state.get("mezzanineCacheIndex");
        if (mezzanine instanceof Map<?, ?> m) {
            String hash = stringVal(m.get("contentHash"), "");
            String cacheKey = stringVal(m.get("cacheKey"), "");
            if (!hash.isBlank() && !cacheKey.isBlank()) {
                hashes.put(cacheKey, hash);
            }
            String taskId = stringVal(m.get("taskId"), "");
            if (!hash.isBlank() && !taskId.isBlank()) {
                hashes.put(taskId, hash);
            }
        }
        return hashes;
    }

    @SuppressWarnings("unchecked")
    private static void collectHashes(Map<String, String> hashes, Object indexObj) {
        if (!(indexObj instanceof Map<?, ?> cacheIndex)) {
            return;
        }
        cacheIndex.forEach((cacheKey, entry) -> {
            if (entry instanceof Map<?, ?> m) {
                String hash = stringVal(m.get("contentHash"), "");
                if (!hash.isBlank()) {
                    hashes.put(String.valueOf(cacheKey), hash);
                    String segmentId = stringVal(m.get("segmentId"), "");
                    if (!segmentId.isBlank()) {
                        hashes.put(segmentId, hash);
                    }
                }
            }
        });
    }

    private static void mergeMezzanineCacheIndex(List<ReusableArtifact> artifacts, Map<?, ?> mezzanine) {
        String taskId = stringVal(mezzanine.get("taskId"), "final_compose");
        String uri = stringVal(mezzanine.get("remoteUri"), stringVal(mezzanine.get("uri"), ""));
        String cacheKey = stringVal(mezzanine.get("cacheKey"), "");
        if (!uri.isBlank() && artifacts.stream().noneMatch(a -> taskId.equals(a.taskId()))) {
            artifacts.add(ReusableArtifact.of(taskId, uri, cacheKey));
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeSegmentCacheIndex(List<ReusableArtifact> artifacts, Map<?, ?> cacheIndex) {
        cacheIndex.forEach((cacheKey, entry) -> {
            if (!(entry instanceof Map<?, ?> m)) {
                return;
            }
            String segmentId = stringVal(m.get("segmentId"), "");
            String uri = stringVal(m.get("remoteUri"), stringVal(m.get("uri"), ""));
            if (segmentId.isBlank() || uri.isBlank()) {
                return;
            }
            if (artifacts.stream().noneMatch(a -> segmentId.equals(a.taskId()))) {
                artifacts.add(ReusableArtifact.of(
                        segmentId, uri, stringVal(cacheKey, stringVal(m.get("cacheKey"), ""))));
            }
        });
    }

    public Map<String, String> indexByCacheKey(List<ReusableArtifact> artifacts) {
        Map<String, String> index = new LinkedHashMap<>();
        for (ReusableArtifact artifact : artifacts) {
            if (artifact.cacheKey() != null && !artifact.cacheKey().isBlank()
                    && artifact.uri() != null && !artifact.uri().isBlank()) {
                index.put(artifact.cacheKey(), artifact.uri());
            }
        }
        return index;
    }

    private static void mergeArtifactMap(List<ReusableArtifact> artifacts, Map<?, ?> map) {
        map.forEach((taskId, uri) -> {
            if (uri != null && !String.valueOf(uri).isBlank()) {
                String id = String.valueOf(taskId);
                if (artifacts.stream().noneMatch(a -> id.equals(a.taskId()))) {
                    artifacts.add(ReusableArtifact.of(id, String.valueOf(uri), ""));
                }
            }
        });
    }

    private static ReusableArtifact mapArtifact(Map<?, ?> m) {
        return new ReusableArtifact(
                stringVal(m.get("artifactId"), stringVal(m.get("taskId"), "")),
                stringVal(m.get("taskId"), ""),
                stringVal(m.get("uri"), ""),
                stringVal(m.get("cacheKey"), ""),
                List.of(),
                stringVal(m.get("scope"), ""));
    }

    public Map<String, String> indexByTaskId(List<ReusableArtifact> artifacts) {
        Map<String, String> index = new LinkedHashMap<>();
        for (ReusableArtifact artifact : artifacts) {
            if (artifact.taskId() != null && artifact.uri() != null && !artifact.uri().isBlank()) {
                index.put(artifact.taskId(), artifact.uri());
            }
        }
        return index;
    }

    public List<ReusableArtifact> snapshotFromPlan(PipelineExecutionPlan plan,
                                                   Map<String, String> completedTaskUris) {
        List<ReusableArtifact> snapshots = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            String uri = completedTaskUris.get(task.taskId());
            if (uri == null && task.parameters() != null) {
                uri = task.parameters().get("reuseArtifactUri");
            }
            if (uri != null && !uri.isBlank()) {
                snapshots.add(new ReusableArtifact(
                        task.taskId(),
                        task.taskId(),
                        uri,
                        task.cacheKey() != null ? task.cacheKey() : "",
                        List.of(),
                        task.type().name()));
            }
        }
        return snapshots;
    }

    private static String stringVal(Object o, String defaultValue) {
        return o != null ? String.valueOf(o) : defaultValue;
    }
}
