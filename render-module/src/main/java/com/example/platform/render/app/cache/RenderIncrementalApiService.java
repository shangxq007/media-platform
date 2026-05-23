package com.example.platform.render.app.cache;

import com.example.platform.render.api.dto.GenerateIncrementalPlanRequest;
import com.example.platform.render.api.dto.IncrementalPlanTaskDto;
import com.example.platform.render.api.dto.IncrementalRenderPlanResponse;
import com.example.platform.render.api.dto.IncrementalReuseArtifactDto;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.app.timeline.IncrementalRenderPlanService;
import com.example.platform.render.domain.timeline.internal.DirtyScope;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RenderIncrementalApiService {

    private final IncrementalRenderPlanService incrementalRenderPlanService;
    private final BaseJobTimelineLoader baseJobTimelineLoader;
    private final RenderCacheTenantGuard tenantGuard;

    public RenderIncrementalApiService(IncrementalRenderPlanService incrementalRenderPlanService,
                                       BaseJobTimelineLoader baseJobTimelineLoader,
                                       RenderCacheTenantGuard tenantGuard) {
        this.incrementalRenderPlanService = incrementalRenderPlanService;
        this.baseJobTimelineLoader = baseJobTimelineLoader;
        this.tenantGuard = tenantGuard;
    }

    public IncrementalRenderPlanResponse previewPlan(String tenantId, String projectId,
                                                     GenerateIncrementalPlanRequest request)
            throws java.io.IOException {
        if (request.baseJobId() != null && !request.baseJobId().isBlank()) {
            tenantGuard.requireBaseJobAccess(tenantId, projectId, request.baseJobId());
        }
        String oldJson = request.oldTimelineJson();
        if ((oldJson == null || oldJson.isBlank()) && request.baseJobId() != null) {
            oldJson = baseJobTimelineLoader.loadInternalTimelineJson(request.baseJobId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Base job has no Internal Timeline 1.0: " + request.baseJobId()));
        }
        if (oldJson == null || oldJson.isBlank()) {
            throw new IllegalArgumentException("oldTimelineJson or baseJobId is required");
        }
        List<ReusableArtifact> reuse = request.reuseArtifacts() != null
                ? request.reuseArtifacts()
                : List.of();
        IncrementalRenderPlan plan = incrementalRenderPlanService.generate(
                request.newTimelineJson(),
                oldJson,
                request.profileOrDefault(),
                request.tierOrDefault(),
                request.outputFormatOrDefault(),
                request.baseJobId(),
                reuse,
                tenantId);
        return toResponse(plan);
    }

    public static IncrementalRenderPlanResponse toResponse(IncrementalRenderPlan plan) {
        PipelineExecutionPlan pipeline = plan.pipelinePlan();
        List<IncrementalPlanTaskDto> tasks = new ArrayList<>();
        for (PipelineTask task : pipeline.tasks()) {
            tasks.add(new IncrementalPlanTaskDto(
                    task.taskId(),
                    task.name(),
                    task.type().name(),
                    task.backend(),
                    task.dependsOn() != null ? task.dependsOn() : List.of(),
                    task.cacheKey() != null ? task.cacheKey() : "",
                    task.parameters()));
        }
        List<IncrementalReuseArtifactDto> reuseDtos = plan.reuse().stream()
                .map(a -> new IncrementalReuseArtifactDto(
                        a.artifactId(),
                        a.taskId(),
                        a.uri(),
                        a.cacheKey() != null ? a.cacheKey() : ""))
                .toList();
        Map<String, Object> metadata = pipeline.metadata() != null
                ? new LinkedHashMap<>(pipeline.metadata())
                : Map.of();
        List<String> dirtyScopes = plan.impact().dirtyScopes().stream()
                .map(DirtyScope::name)
                .toList();
        return new IncrementalRenderPlanResponse(
                plan.mode(),
                plan.fullReRenderRequired(),
                plan.baseRevision(),
                plan.targetRevision(),
                plan.executeTaskIds(),
                plan.reuseTaskIds(),
                dirtyScopes,
                plan.diff().changes().size(),
                pipeline.planId(),
                pipeline.timelineId(),
                pipeline.finalComposer().name().toLowerCase(),
                metadata,
                tasks,
                reuseDtos);
    }
}
