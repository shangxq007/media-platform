package com.example.platform.render.app.timeline;

import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.domain.planning.ReusableArtifact;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves advisory reuse candidates from explicit input or prior job execution state.
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
            map.forEach((taskId, priorOutput) -> {
                if (priorOutput != null) {
                    artifacts.add(ReusableArtifact.of(String.valueOf(taskId), ""));
                }
            });
        }
        Object reuseList = state.get("reuseCandidates");
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

    private static void mergeMezzanineCacheIndex(List<ReusableArtifact> artifacts, Map<?, ?> mezzanine) {
        String taskId = stringVal(mezzanine.get("taskId"), "final_compose");
        String cacheKey = stringVal(mezzanine.get("cacheKey"), "");
        if (!taskId.isBlank() && artifacts.stream().noneMatch(a -> taskId.equals(a.taskId()))) {
            artifacts.add(ReusableArtifact.of(taskId, cacheKey));
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeSegmentCacheIndex(List<ReusableArtifact> artifacts, Map<?, ?> cacheIndex) {
        cacheIndex.forEach((cacheKey, entry) -> {
            if (!(entry instanceof Map<?, ?> m)) {
                return;
            }
            String segmentId = stringVal(m.get("segmentId"), "");
            if (segmentId.isBlank()) {
                return;
            }
            if (artifacts.stream().noneMatch(a -> segmentId.equals(a.taskId()))) {
                artifacts.add(ReusableArtifact.of(
                        segmentId, stringVal(cacheKey, stringVal(m.get("cacheKey"), ""))));
            }
        });
    }

    private static void mergeArtifactMap(List<ReusableArtifact> artifacts, Map<?, ?> map) {
        map.forEach((taskId, priorOutput) -> {
            if (priorOutput != null) {
                String id = String.valueOf(taskId);
                if (artifacts.stream().noneMatch(a -> id.equals(a.taskId()))) {
                    artifacts.add(ReusableArtifact.of(id, ""));
                }
            }
        });
    }

    private static ReusableArtifact mapArtifact(Map<?, ?> m) {
        return new ReusableArtifact(
                stringVal(m.get("artifactId"), stringVal(m.get("taskId"), "")),
                stringVal(m.get("taskId"), ""),
                stringVal(m.get("cacheKey"), ""),
                List.of(),
                stringVal(m.get("scope"), ""));
    }

    public Map<String, ReusableArtifact> indexByTaskId(List<ReusableArtifact> artifacts) {
        Map<String, ReusableArtifact> index = new LinkedHashMap<>();
        for (ReusableArtifact artifact : artifacts) {
            if (artifact.taskId() != null && !artifact.taskId().isBlank()) {
                index.put(artifact.taskId(), artifact);
            }
        }
        return index;
    }

    public List<ReusableArtifact> snapshotFromPlan(PipelineExecutionPlan plan,
                                                   Map<String, String> completedTaskOutputs) {
        List<ReusableArtifact> snapshots = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            if (completedTaskOutputs.containsKey(task.taskId())) {
                snapshots.add(new ReusableArtifact(
                        task.taskId(),
                        task.taskId(),
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
