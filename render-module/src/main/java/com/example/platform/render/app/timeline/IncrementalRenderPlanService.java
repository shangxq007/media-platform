package com.example.platform.render.app.timeline;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.internal.DirtyScope;
import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import com.example.platform.render.domain.timeline.internal.RenderImpactResult;
import com.example.platform.render.domain.timeline.internal.SemanticDiffResult;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Builds incremental {@link PipelineExecutionPlan} from semantic diff + impact analysis.
 */
@Service
public class IncrementalRenderPlanService {

    private final TimelineSemanticDiffService semanticDiffService;
    private final RenderImpactAnalyzer impactAnalyzer;
    private final InternalTimelineAdapter internalTimelineAdapter;
    private final RenderPlannerService renderPlannerService;
    private final RenderArtifactRegistry artifactRegistry;
    private final TimelineCanonicalizer canonicalizer;
    private final SegmentTimelinePlanner segmentTimelinePlanner;
    private final RenderCacheUriResolver cacheUriResolver;
    private final SegmentPlanFilter segmentPlanFilter;
    private final RenderCacheReuseValidator cacheReuseValidator;

    public IncrementalRenderPlanService(TimelineSemanticDiffService semanticDiffService,
                                        RenderImpactAnalyzer impactAnalyzer,
                                        InternalTimelineAdapter internalTimelineAdapter,
                                        RenderPlannerService renderPlannerService,
                                        RenderArtifactRegistry artifactRegistry,
                                        TimelineCanonicalizer canonicalizer,
                                        SegmentTimelinePlanner segmentTimelinePlanner,
                                        RenderCacheUriResolver cacheUriResolver,
                                        SegmentPlanFilter segmentPlanFilter,
                                        RenderCacheReuseValidator cacheReuseValidator) {
        this.semanticDiffService = semanticDiffService;
        this.impactAnalyzer = impactAnalyzer;
        this.internalTimelineAdapter = internalTimelineAdapter;
        this.renderPlannerService = renderPlannerService;
        this.artifactRegistry = artifactRegistry;
        this.canonicalizer = canonicalizer;
        this.segmentTimelinePlanner = segmentTimelinePlanner;
        this.cacheUriResolver = cacheUriResolver;
        this.segmentPlanFilter = segmentPlanFilter;
        this.cacheReuseValidator = cacheReuseValidator;
    }

    public IncrementalRenderPlan generate(String newTimelineJson,
                                          String oldTimelineJson,
                                          String profile,
                                          String tier,
                                          String outputFormat,
                                          String baseJobId,
                                          List<ReusableArtifact> explicitReuse) throws java.io.IOException {
        return generate(newTimelineJson, oldTimelineJson, profile, tier, outputFormat,
                baseJobId, explicitReuse, null);
    }

    public IncrementalRenderPlan generate(String newTimelineJson,
                                          String oldTimelineJson,
                                          String profile,
                                          String tier,
                                          String outputFormat,
                                          String baseJobId,
                                          List<ReusableArtifact> explicitReuse,
                                          String tenantId) throws java.io.IOException {
        SemanticDiffResult diff = oldTimelineJson != null && !oldTimelineJson.isBlank()
                ? semanticDiffService.diff(oldTimelineJson, newTimelineJson)
                : emptyDiff(newTimelineJson);
        RenderImpactResult impact = impactAnalyzer.analyze(diff);

        TimelineSpec spec = internalTimelineAdapter.toSpec(newTimelineJson)
                .orElseThrow(() -> new IllegalArgumentException("Invalid timeline JSON"));

        PipelineExecutionPlan fullPlan = renderPlannerService.generatePlan(
                spec, profile, tier, outputFormat, newTimelineJson);

        List<ReusableArtifact> reusePool = artifactRegistry.resolve(tenantId, baseJobId, explicitReuse);
        HashFilterResult hashFilter = resolveReuseUris(reusePool, tenantId, baseJobId);
        Map<String, String> reuseByTask = hashFilter.validUris();
        Set<String> hashInvalidatedTaskIds = hashFilter.hashInvalidatedTaskIds();

        Set<String> dirtySegmentIds = segmentTimelinePlanner.planFromTimelineJson(newTimelineJson)
                .map(plan -> segmentTimelinePlanner.dirtySegmentTaskIds(diff, newTimelineJson, plan))
                .orElse(Set.of());

        boolean segmentPolicyEnabled = isSegmentPolicyEnabled(fullPlan);
        AppliedPlan applied = impact.fullReRenderRequired()
                ? applyFull(fullPlan)
                : applyIncremental(fullPlan, impact, reuseByTask, dirtySegmentIds, segmentPolicyEnabled,
                        hashInvalidatedTaskIds);

        Set<String> targetSegmentIds = SegmentPlanFilter.parseTargetSegmentIds(spec.metadata());
        if (!targetSegmentIds.isEmpty()) {
            PipelineExecutionPlan filtered = segmentPlanFilter.restrictToTargetSegments(
                    applied.plan(), targetSegmentIds, reuseByTask);
            applied = recountAfterFilter(filtered, applied.reuseArtifacts());
        }

        Map<String, String> meta = new LinkedHashMap<>(applied.plan().metadata() != null
                ? applied.plan().metadata() : Map.of());
        meta.put("mode", impact.fullReRenderRequired()
                ? IncrementalRenderPlan.MODE_FULL
                : IncrementalRenderPlan.MODE_INCREMENTAL);
        meta.put("baseRevision", String.valueOf(diff.oldRevision()));
        meta.put("targetRevision", String.valueOf(diff.newRevision()));
        meta.put("executeTaskCount", String.valueOf(applied.executeTaskIds().size()));
        meta.put("reuseTaskCount", String.valueOf(applied.reuseTaskIds().size()));
        if (!hashInvalidatedTaskIds.isEmpty()) {
            meta.put("hashInvalidatedTaskIds", String.join(",", hashInvalidatedTaskIds));
            meta.put("hashInvalidatedCount", String.valueOf(hashInvalidatedTaskIds.size()));
        }

        PipelineExecutionPlan planWithMeta = new PipelineExecutionPlan(
                applied.plan().planId(),
                applied.plan().timelineId(),
                applied.plan().finalComposer(),
                applied.plan().tasks(),
                meta);

        return new IncrementalRenderPlan(
                planWithMeta.planId(),
                meta.get("mode"),
                diff.oldRevision(),
                diff.newRevision(),
                impact.fullReRenderRequired(),
                diff,
                impact,
                planWithMeta,
                applied.reuseArtifacts(),
                applied.executeTaskIds(),
                applied.reuseTaskIds(),
                meta);
    }

    private SemanticDiffResult emptyDiff(String newJson) throws java.io.IOException {
        var canon = InternalTimelineJson.parse(
                canonicalizer.canonicalize(newJson).timelineJson());
        return new SemanticDiffResult(
                InternalTimelineJson.timelineId(canon),
                InternalTimelineJson.timelineId(canon),
                InternalTimelineJson.revision(canon),
                InternalTimelineJson.revision(canon),
                InternalTimelineJson.schemaVersion(canon),
                List.of(),
                true);
    }

    private AppliedPlan applyFull(PipelineExecutionPlan full) {
        List<String> execute = full.tasks().stream().map(PipelineTask::taskId).toList();
        List<PipelineTask> tasks = full.tasks().stream()
                .map(t -> withMode(t, "execute", null))
                .toList();
        return new AppliedPlan(
                new PipelineExecutionPlan(full.planId(), full.timelineId(), full.finalComposer(), tasks, full.metadata()),
                List.of(),
                execute,
                List.of());
    }

    private HashFilterResult resolveReuseUris(List<ReusableArtifact> reusePool,
                                            String tenantId,
                                            String baseJobId) {
        Map<String, String> uris = artifactRegistry.indexByTaskId(reusePool);
        Map<String, String> cacheKeys = new LinkedHashMap<>();
        for (ReusableArtifact artifact : reusePool) {
            if (artifact.taskId() != null) {
                cacheKeys.put(artifact.taskId(), artifact.cacheKey());
            }
        }
        Map<String, String> resolved = cacheUriResolver.resolveTaskIndex(uris, cacheKeys, tenantId);
        Map<String, String> byCacheKey = artifactRegistry.indexByCacheKey(reusePool);
        byCacheKey.forEach((cacheKey, uri) ->
                resolved.putIfAbsent(cacheKey, cacheUriResolver.resolve(uri, cacheKey, tenantId)));
        return filterByContentHash(resolved, cacheKeys, baseJobId);
    }

    private HashFilterResult filterByContentHash(Map<String, String> resolved,
                                                 Map<String, String> cacheKeys,
                                                 String baseJobId) {
        if (cacheReuseValidator == null || !cacheReuseValidator.isValidationEnabled()
                || baseJobId == null || baseJobId.isBlank()) {
            return new HashFilterResult(resolved, Set.of());
        }
        Map<String, String> expectedHashes = artifactRegistry.loadContentHashes(baseJobId);
        if (expectedHashes.isEmpty()) {
            return new HashFilterResult(resolved, Set.of());
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        Set<String> invalidated = new LinkedHashSet<>();
        resolved.forEach((taskId, uri) -> {
            String cacheKey = cacheKeys.get(taskId);
            String expected = expectedHashes.get(taskId);
            if (expected == null && cacheKey != null) {
                expected = expectedHashes.get(cacheKey);
            }
            if (expected != null && !expected.isBlank()
                    && !cacheReuseValidator.validateLocalArtifact(uri, expected)) {
                invalidated.add(taskId);
                return;
            }
            filtered.put(taskId, uri);
        });
        return new HashFilterResult(filtered, Set.copyOf(invalidated));
    }

    private record HashFilterResult(Map<String, String> validUris, Set<String> hashInvalidatedTaskIds) {}

    private AppliedPlan applyIncremental(PipelineExecutionPlan full,
                                         RenderImpactResult impact,
                                         Map<String, String> reuseByTask,
                                         Set<String> dirtySegmentIds,
                                         boolean segmentPolicyEnabled,
                                         Set<String> hashInvalidatedTaskIds) {
        Set<PipelineTaskType> dirtyTypes = dirtyTaskTypes(impact.dirtyScopes());
        Set<String> dirtyTaskIds = dirtyTaskIds(impact, full);
        dirtyTaskIds.addAll(dirtySegmentIds);

        List<PipelineTask> tasks = new ArrayList<>();
        List<String> executeIds = new ArrayList<>();
        List<String> reuseIds = new ArrayList<>();
        List<ReusableArtifact> reuseArtifacts = new ArrayList<>();

        for (PipelineTask task : full.tasks()) {
            boolean reuse = shouldReuseTask(task, dirtyTypes, dirtyTaskIds, impact, dirtySegmentIds, segmentPolicyEnabled)
                    && !hashInvalidatedTaskIds.contains(task.taskId());
            if (reuse) {
                String uri = resolveReuseUri(task, reuseByTask);
                tasks.add(withMode(task, "reuse", uri));
                reuseIds.add(task.taskId());
                if (uri != null) {
                    reuseArtifacts.add(ReusableArtifact.of(task.taskId(), uri, task.cacheKey()));
                }
            } else {
                tasks.add(withMode(task, "execute", null));
                executeIds.add(task.taskId());
            }
        }

        return new AppliedPlan(
                new PipelineExecutionPlan(full.planId(), full.timelineId(), full.finalComposer(), tasks, full.metadata()),
                reuseArtifacts,
                executeIds,
                reuseIds);
    }

    private boolean shouldReuseTask(PipelineTask task,
                                    Set<PipelineTaskType> dirtyTypes,
                                    Set<String> dirtyTaskIds,
                                    RenderImpactResult impact,
                                    Set<String> dirtySegmentIds,
                                    boolean segmentPolicyEnabled) {
        if (impact.dirtyScopes().contains(DirtyScope.FULL_TIMELINE)) {
            return false;
        }
        if (dirtyTaskIds.contains(task.taskId())) {
            return false;
        }
        if (task.type() == PipelineTaskType.SEGMENT_RENDER) {
            if (segmentPolicyEnabled && upstreamOverlayDirty(dirtyTypes)) {
                return false;
            }
            return !dirtySegmentIds.contains(task.taskId())
                    && downstreamClean(task, dirtyTypes, dirtyTaskIds, dirtySegmentIds);
        }
        if (task.type() == PipelineTaskType.FINAL_COMPOSE && segmentPolicyEnabled) {
            if (upstreamOverlayDirty(dirtyTypes) || !dirtySegmentIds.isEmpty()) {
                return false;
            }
            return downstreamClean(task, dirtyTypes, dirtyTaskIds, dirtySegmentIds);
        }
        if (dirtyTypes.contains(task.type())) {
            return false;
        }
        if (impact.dirtyScopes().size() == 1 && impact.dirtyScopes().contains(DirtyScope.PACKAGING)) {
            return task.type() != PipelineTaskType.PACKAGING && task.type() != PipelineTaskType.QA;
        }
        if (impact.dirtyScopes().contains(DirtyScope.STEM)
                && !impact.dirtyScopes().contains(DirtyScope.LAYER)
                && !impact.dirtyScopes().contains(DirtyScope.CLIP)) {
            return task.type() == PipelineTaskType.MLT_MULTITRACK
                    || task.type() == PipelineTaskType.EFFECTS
                    || task.type() == PipelineTaskType.SUBTITLES
                    || task.type() == PipelineTaskType.SKIA_OVERLAY
                    || task.type() == PipelineTaskType.FINAL_COMPOSE
                    || task.type() == PipelineTaskType.EXTERNAL_RENDER;
        }
        return downstreamClean(task, dirtyTypes, dirtyTaskIds, dirtySegmentIds);
    }

    private static boolean upstreamOverlayDirty(Set<PipelineTaskType> dirtyTypes) {
        return dirtyTypes.contains(PipelineTaskType.SUBTITLES)
                || dirtyTypes.contains(PipelineTaskType.SKIA_OVERLAY)
                || dirtyTypes.contains(PipelineTaskType.EFFECTS);
    }

    private static boolean isSegmentPolicyEnabled(PipelineExecutionPlan plan) {
        return plan.metadata() != null
                && "true".equalsIgnoreCase(plan.metadata().get("segmentPolicyEnabled"));
    }

    private boolean downstreamClean(PipelineTask task,
                                    Set<PipelineTaskType> dirtyTypes,
                                    Set<String> dirtyTaskIds,
                                    Set<String> dirtySegmentIds) {
        if (task.dependsOn() == null) {
            return true;
        }
        for (String dep : task.dependsOn()) {
            if (dirtyTaskIds.contains(dep) || dirtySegmentIds.contains(dep)) {
                return false;
            }
        }
        return true;
    }

    private Set<PipelineTaskType> dirtyTaskTypes(Set<DirtyScope> scopes) {
        EnumSet<PipelineTaskType> types = EnumSet.noneOf(PipelineTaskType.class);
        for (DirtyScope scope : scopes) {
            switch (scope) {
                case PACKAGING -> types.add(PipelineTaskType.PACKAGING);
                case OUTPUT -> {
                    types.add(PipelineTaskType.TRANSCODE);
                    types.add(PipelineTaskType.PACKAGING);
                }
                case STEM -> types.add(PipelineTaskType.FINAL_COMPOSE);
                case LAYER -> {
                    types.add(PipelineTaskType.SUBTITLES);
                    types.add(PipelineTaskType.SKIA_OVERLAY);
                    types.add(PipelineTaskType.EXTERNAL_RENDER);
                }
                case CLIP, SEGMENT, TRANSITION -> {
                    types.add(PipelineTaskType.MLT_MULTITRACK);
                    types.add(PipelineTaskType.EFFECTS);
                    types.add(PipelineTaskType.FINAL_COMPOSE);
                    types.add(PipelineTaskType.EXTERNAL_RENDER);
                    // SEGMENT_RENDER reuse decided per-segment via dirtySegmentIds
                }
                case PROJECT, FULL_TIMELINE, ASSET -> types.addAll(EnumSet.allOf(PipelineTaskType.class));
                default -> { }
            }
        }
        return types;
    }

    private Set<String> dirtyTaskIds(RenderImpactResult impact, PipelineExecutionPlan plan) {
        Set<String> ids = new LinkedHashSet<>();
        for (EntityRef entity : impact.dirtyEntities()) {
            switch (entity.kind()) {
                case EXTERNAL_NODE -> ids.add("xr_" + entity.id());
                case LAYER -> {
                    if (entity.id().contains("sticker") || entity.id().contains("logo")) {
                        ids.add("skia_overlay");
                    } else {
                        ids.add("subtitles");
                    }
                }
                case PACKAGING -> ids.add("packaging");
                default -> { }
            }
        }
        for (PipelineTask task : plan.tasks()) {
            if (impact.dirtyScopes().contains(DirtyScope.PACKAGING)
                    && task.type() == PipelineTaskType.PACKAGING) {
                ids.add(task.taskId());
            }
        }
        return ids;
    }

    private AppliedPlan recountAfterFilter(PipelineExecutionPlan plan, List<ReusableArtifact> reuseArtifacts) {
        List<String> executeIds = new ArrayList<>();
        List<String> reuseIds = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            String mode = task.parameters() != null ? task.parameters().get("incrementalMode") : null;
            if ("reuse".equals(mode)) {
                reuseIds.add(task.taskId());
            } else {
                executeIds.add(task.taskId());
            }
        }
        return new AppliedPlan(plan, reuseArtifacts, executeIds, reuseIds);
    }

    private static String resolveReuseUri(PipelineTask task, Map<String, String> reuseByTask) {
        String uri = reuseByTask.get(task.taskId());
        if ((uri == null || uri.isBlank()) && task.cacheKey() != null) {
            uri = reuseByTask.get(task.cacheKey());
        }
        return uri;
    }

    private static PipelineTask withMode(PipelineTask task, String mode, String reuseUri) {
        Map<String, String> params = new LinkedHashMap<>(
                task.parameters() != null ? task.parameters() : Map.of());
        params.put("incrementalMode", mode);
        if ("reuse".equals(mode)) {
            params.put("skipExecution", "true");
            if (reuseUri != null) {
                params.put("reuseArtifactUri", reuseUri);
            }
        } else {
            params.remove("skipExecution");
            params.remove("reuseArtifactUri");
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

    private record AppliedPlan(
            PipelineExecutionPlan plan,
            List<ReusableArtifact> reuseArtifacts,
            List<String> executeTaskIds,
            List<String> reuseTaskIds) {}
}
