package com.example.platform.render.app.timeline;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Restricts a pipeline plan to a subset of segment render tasks (partial segment render).
 */
@Service
public class SegmentPlanFilter {

    public PipelineExecutionPlan restrictToTargetSegments(PipelineExecutionPlan plan,
                                                          Set<String> targetSegmentIds,
                                                          Map<String, String> reuseUriBySegmentId) {
        if (plan == null || targetSegmentIds == null || targetSegmentIds.isEmpty()) {
            return plan;
        }
        List<PipelineTask> tasks = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            if (task.type() != PipelineTaskType.SEGMENT_RENDER) {
                tasks.add(task);
                continue;
            }
            if (targetSegmentIds.contains(task.taskId())) {
                tasks.add(task);
                continue;
            }
            String reuseUri = reuseUriBySegmentId != null ? reuseUriBySegmentId.get(task.taskId()) : null;
            tasks.add(withForcedReuse(task, reuseUri));
        }
        Map<String, String> meta = new LinkedHashMap<>(plan.metadata() != null ? plan.metadata() : Map.of());
        meta.put("targetSegmentIds", String.join(",", targetSegmentIds));
        meta.put("segmentFilterMode", "PARTIAL");
        return new PipelineExecutionPlan(
                plan.planId(),
                plan.timelineId(),
                plan.finalComposer(),
                List.copyOf(tasks),
                meta);
    }

    public static Set<String> parseTargetSegmentIds(Map<String, String> metadata) {
        if (metadata == null || !metadata.containsKey("platform.targetSegmentIds")) {
            return Set.of();
        }
        String raw = metadata.get("platform.targetSegmentIds");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Set.of(raw.split(","));
    }

    public static void embedTargetSegmentIds(Map<String, String> metadata, List<String> segmentIds) {
        if (metadata == null || segmentIds == null || segmentIds.isEmpty()) {
            return;
        }
        metadata.put("platform.targetSegmentIds", String.join(",", segmentIds));
    }

    private static PipelineTask withForcedReuse(PipelineTask task, String reuseUri) {
        Map<String, String> params = new LinkedHashMap<>(task.parameters() != null ? task.parameters() : Map.of());
        params.put("incrementalMode", "reuse");
        params.put("skipExecution", "true");
        if (reuseUri != null && !reuseUri.isBlank()) {
            params.put("reuseArtifactUri", reuseUri);
        }
        return new PipelineTask(
                task.taskId(),
                task.name(),
                task.type(),
                task.backend(),
                task.dependsOn(),
                task.cacheKey(),
                task.intermediateFormat(),
                Map.copyOf(params));
    }
}
