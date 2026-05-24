package com.example.platform.render.app.planner;

import com.example.platform.render.app.MultiProviderPipelineService;
import com.example.platform.render.app.timeline.MezzanineCachePublisher;
import com.example.platform.render.app.timeline.SegmentArtifactUploadService;
import com.example.platform.render.app.timeline.SegmentCachePublisher;
import com.example.platform.render.app.timeline.SegmentTimelinePlanner;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Executes {@link PipelineExecutionPlan} DAG: external renders first, then multi-provider pipeline.
 */
@Service
public class PipelineDagExecutorService {

    private static final Logger log = LoggerFactory.getLogger(PipelineDagExecutorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RenderPlannerService renderPlannerService;
    private final RenderProviderRegistry providerRegistry;
    private final MultiProviderPipelineService multiProviderPipelineService;
    private final TimelineExtensionsReader extensionsReader;
    private final PipelinePlanPersistenceService planPersistence;
    private final SegmentTimelinePlanner segmentTimelinePlanner;
    private final SegmentCachePublisher segmentCachePublisher;
    private final java.util.Optional<SegmentArtifactUploadService> segmentArtifactUploadService;
    private final MezzanineCachePublisher mezzanineCachePublisher;
    @Value("${render.pipeline.dag.enabled:true}")
    private boolean dagEnabled;
    @Value("${render.pipeline.dag.parallel-external:true}")
    private boolean parallelExternal;
    private final ExecutorService externalRenderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PipelineDagExecutorService(RenderPlannerService renderPlannerService,
                                        RenderProviderRegistry providerRegistry,
                                        MultiProviderPipelineService multiProviderPipelineService,
                                        TimelineExtensionsReader extensionsReader,
                                        PipelinePlanPersistenceService planPersistence,
                                        SegmentTimelinePlanner segmentTimelinePlanner,
                                        SegmentCachePublisher segmentCachePublisher,
                                        java.util.Optional<SegmentArtifactUploadService> segmentArtifactUploadService,
                                        MezzanineCachePublisher mezzanineCachePublisher) {
        this.renderPlannerService = renderPlannerService;
        this.providerRegistry = providerRegistry;
        this.multiProviderPipelineService = multiProviderPipelineService;
        this.extensionsReader = extensionsReader;
        this.planPersistence = planPersistence;
        this.segmentTimelinePlanner = segmentTimelinePlanner;
        this.segmentCachePublisher = segmentCachePublisher;
        this.segmentArtifactUploadService = segmentArtifactUploadService;
        this.mezzanineCachePublisher = mezzanineCachePublisher;
    }

    public boolean shouldExecuteAsDag(TimelineSpec timeline, String profile) {
        if (!dagEnabled || timeline == null) {
            return false;
        }

        TimelineExtensions ext = extensionsReader.fromSpec(timeline);

        // 1. External render nodes always need DAG
        if (!ext.externalRenderNodes().isEmpty()) {
            return true;
        }

        // 2. Streaming output formats need DAG (packaging stage)
        String format = timeline.outputSpec() != null ? timeline.outputSpec().format() : "mp4";
        if (format != null && (format.equalsIgnoreCase("dash")
                || format.equalsIgnoreCase("hls")
                || format.equalsIgnoreCase("cmaf")
                || format.equalsIgnoreCase("dash_drm"))) {
            return true;
        }

        // 3. Count independent rendering concerns
        int concerns = 0;

        long videoTracks = timeline.tracks().stream()
                .filter(t -> t.type() == com.example.platform.render.domain.timeline.TimelineTrack.TrackType.VIDEO)
                .count();
        if (videoTracks >= 2) concerns++;

        boolean hasAudioTracks = timeline.tracks().stream()
                .anyMatch(t -> t.type() == com.example.platform.render.domain.timeline.TimelineTrack.TrackType.AUDIO
                        && !t.muted() && t.clips() != null && !t.clips().isEmpty());
        if (hasAudioTracks && videoTracks >= 1) concerns++;

        boolean hasSubtitles = (timeline.textOverlays() != null && !timeline.textOverlays().isEmpty())
                || timeline.tracks().stream().anyMatch(t ->
                        t.type() == com.example.platform.render.domain.timeline.TimelineTrack.TrackType.SUBTITLE);
        if (hasSubtitles) concerns++;

        boolean hasEffects = timeline.tracks().stream()
                .flatMap(t -> t.clips() != null ? t.clips().stream() : java.util.stream.Stream.empty())
                .anyMatch(c -> c.effects() != null && !c.effects().isEmpty());
        if (hasEffects) concerns++;

        // Multiple independent concerns benefit from DAG orchestration
        return concerns >= 2;
    }

    public DagExecutionResult execute(String jobId, TimelineSpec timeline, String profile,
                                      String tier, String outputFormat) {
        PipelineExecutionPlan plan = renderPlannerService.generatePlan(timeline, profile, tier, outputFormat);
        return executeWithPlan(jobId, timeline, plan, profile, tier, outputFormat);
    }

    /**
     * Executes a pre-built plan (e.g. from {@link com.example.platform.render.app.timeline.IncrementalRenderPlanService}).
     */
    public DagExecutionResult executeWithPlan(String jobId, TimelineSpec timeline, PipelineExecutionPlan plan,
                                               String profile, String tier, String outputFormat) {
        planPersistence.savePlan(jobId, plan);
        planPersistence.updateWaveState(jobId, -1, "STARTED", null);

        Map<String, String> completedArtifacts = new LinkedHashMap<>();
        List<DagTaskResult> taskResults = new ArrayList<>();
        int waveIndex = 0;

        for (List<PipelineTask> wave : PipelineDagTopology.executionWaves(plan)) {
            List<PipelineTask> externalTasks = wave.stream()
                    .filter(t -> t.type() == PipelineTaskType.EXTERNAL_RENDER)
                    .toList();
            if (externalTasks.isEmpty()) {
                waveIndex++;
                continue;
            }

            planPersistence.updateWaveState(jobId, waveIndex, "EXECUTING", null);

            List<DagTaskResult> waveResults = parallelExternal
                    ? executeExternalWaveParallel(jobId, externalTasks, timeline, profile, completedArtifacts)
                    : executeExternalWaveSequential(jobId, externalTasks, timeline, profile, completedArtifacts);

            List<Map<String, String>> waveTaskResults = new ArrayList<>();
            for (DagTaskResult result : waveResults) {
                taskResults.add(result);
                waveTaskResults.add(Map.of(
                        "taskId", result.taskId(),
                        "success", String.valueOf(result.success()),
                        "storageUri", result.storageUri() != null ? result.storageUri() : "",
                        "error", result.errorMessage() != null ? result.errorMessage() : ""));
                if (!result.success()) {
                    planPersistence.updateWaveState(jobId, waveIndex, "FAILED", waveTaskResults);
                    planPersistence.markPlanFailed(jobId, result.taskId(), result.errorMessage());
                    return DagExecutionResult.failed(plan, taskResults, result.errorMessage());
                }
                if (result.storageUri() != null) {
                    completedArtifacts.put(result.taskId(), result.storageUri());
                }
            }

            planPersistence.updateWaveState(jobId, waveIndex, "COMPLETED", waveTaskResults);
            waveIndex++;
        }

        TimelineSpec enriched = enrichTimelineWithExternalArtifacts(timeline, completedArtifacts);
        String timelineJson = serializeTimeline(enriched);

        MultiProviderPipelineService.PipelineResult pipelineResult =
                multiProviderPipelineService.executePipeline(jobId, enriched, profile, tier, outputFormat, plan);

        Map<String, Object> executionState = new LinkedHashMap<>();
        executionState.put("planId", plan.planId());
        executionState.put("externalArtifacts", completedArtifacts);
        executionState.put("pipelineSuccess", pipelineResult.success());
        executionState.put("stageCount", pipelineResult.stages().size());
        executionState.put("mode", plan.metadata() != null ? plan.metadata().getOrDefault("mode", "FULL") : "FULL");
        executionState.put("reuseArtifacts", collectReuseArtifacts(plan));
        if (pipelineResult.stages() != null && !pipelineResult.stages().isEmpty()) {
            Map<String, String> stageArtifacts =
                    IncrementalPipelineSupport.stageArtifactIndex(pipelineResult.stages());
            executionState.put("pipelineStageArtifacts", stageArtifacts);
            Map<String, String> segmentArtifacts = new LinkedHashMap<>();
            for (PipelineTask task : plan.tasks()) {
                if (task.type() == PipelineTaskType.SEGMENT_RENDER) {
                    String uri = stageArtifacts.get(task.taskId());
                    if (uri != null && !uri.isBlank()) {
                        segmentArtifacts.put(task.taskId(), uri);
                    }
                }
            }
            if (!segmentArtifacts.isEmpty()) {
                executionState.put("segmentArtifacts", segmentArtifacts);
                String tenantId = tenantIdFromTimeline(timeline);
                publishSegmentCacheIndex(timelineJson, tenantId, segmentArtifacts, executionState);
            }
            publishMezzanineCacheIndex(
                    plan, tenantIdFromTimeline(timeline), stageArtifacts, executionState);
        }
        planPersistence.saveExecutionState(jobId, executionState);

        if (!pipelineResult.success()) {
            return DagExecutionResult.failed(plan, taskResults, pipelineResult.errorMessage());
        }

        String storageUri = pipelineResult.storageUri();
        if (storageUri == null && !pipelineResult.stages().isEmpty()) {
            storageUri = pipelineResult.stages().get(pipelineResult.stages().size() - 1).storageUri();
        }

        if (pipelineResult.success()) {
            planPersistence.markPlanCompleted(jobId);
        } else {
            planPersistence.markPlanFailed(jobId, "pipeline", pipelineResult.errorMessage());
        }

        return DagExecutionResult.success(plan, taskResults, pipelineResult, storageUri, timelineJson);
    }

    private List<DagTaskResult> executeExternalWaveSequential(String jobId, List<PipelineTask> tasks,
                                                              TimelineSpec timeline, String profile,
                                                              Map<String, String> completedArtifacts) {
        List<DagTaskResult> results = new ArrayList<>();
        for (PipelineTask task : tasks) {
            results.add(executeExternalTask(jobId, task, timeline, profile, completedArtifacts));
        }
        return results;
    }

    private List<DagTaskResult> executeExternalWaveParallel(String jobId, List<PipelineTask> tasks,
                                                          TimelineSpec timeline, String profile,
                                                          Map<String, String> priorArtifacts) {
        ConcurrentHashMap<String, String> liveArtifacts = new ConcurrentHashMap<>(priorArtifacts);
        List<PipelineTask> remaining = new ArrayList<>(tasks);
        List<DagTaskResult> results = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<PipelineTask> ready = remaining.stream()
                    .filter(t -> dependenciesSatisfied(t, liveArtifacts))
                    .toList();
            if (ready.isEmpty()) {
                PipelineTask fallback = remaining.get(0);
                ready = List.of(fallback);
            }

            List<PipelineTask> batch = List.copyOf(ready);
            List<CompletableFuture<DagTaskResult>> futures = batch.stream()
                    .map(task -> CompletableFuture.supplyAsync(
                            () -> executeExternalTask(jobId, task, timeline, profile, liveArtifacts),
                            externalRenderExecutor))
                    .toList();

            for (int i = 0; i < batch.size(); i++) {
                DagTaskResult result = futures.get(i).join();
                results.add(result);
                remaining.remove(batch.get(i));
                if (!result.success()) {
                    return results;
                }
                if (result.storageUri() != null) {
                    liveArtifacts.put(result.taskId(), result.storageUri());
                }
            }
        }
        return results;
    }

    private static List<Map<String, String>> collectReuseArtifacts(PipelineExecutionPlan plan) {
        List<Map<String, String>> reuse = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            if (task.parameters() == null) {
                continue;
            }
            if ("reuse".equalsIgnoreCase(task.parameters().get("incrementalMode"))
                    || "true".equalsIgnoreCase(task.parameters().get("skipExecution"))) {
                reuse.add(Map.of(
                        "taskId", task.taskId(),
                        "uri", task.parameters().getOrDefault("reuseArtifactUri", ""),
                        "cacheKey", task.cacheKey() != null ? task.cacheKey() : ""));
            }
        }
        return reuse;
    }

    private static boolean shouldSkipIncrementalReuse(PipelineTask task) {
        if (task.parameters() == null) {
            return false;
        }
        return "true".equalsIgnoreCase(task.parameters().get("skipExecution"))
                || "reuse".equalsIgnoreCase(task.parameters().get("incrementalMode"));
    }

    private boolean dependenciesSatisfied(PipelineTask task, Map<String, String> artifacts) {
        if (task.dependsOn() == null || task.dependsOn().isEmpty()) {
            return true;
        }
        return task.dependsOn().stream().allMatch(dep ->
                artifacts.containsKey(dep) && artifacts.get(dep) != null && !artifacts.get(dep).isBlank());
    }

    private DagTaskResult executeExternalTask(String jobId, PipelineTask task, TimelineSpec parent,
                                              String profile, Map<String, String> priorArtifacts) {
        if (shouldSkipIncrementalReuse(task)) {
            String uri = task.parameters().get("reuseArtifactUri");
            log.info("Skipping external render task {} (incremental reuse) uri={}", task.taskId(), uri);
            String effectiveUri = uri != null && !uri.isBlank() ? uri : "reuse://" + task.taskId();
            return DagTaskResult.reused(task.taskId(), effectiveUri);
        }
        Optional<RenderProvider> providerOpt = providerRegistry.getProvider(task.backend());
        if (providerOpt.isEmpty()) {
            return DagTaskResult.failed(task.taskId(), "Provider not registered: " + task.backend());
        }
        try {
            String subJobId = jobId + "-" + task.taskId();
            String script = buildExternalRenderScript(parent, task, priorArtifacts);
            RenderProvider.RenderResult result = providerOpt.get().render(subJobId, script, profile);
            log.info("External render task {} completed artifact={}", task.taskId(), result.storageUri());
            return DagTaskResult.success(task.taskId(), result.storageUri(), result.artifactId());
        } catch (Exception e) {
            log.error("External render task {} failed", task.taskId(), e);
            return DagTaskResult.failed(task.taskId(), e.getMessage());
        }
    }

    private String buildExternalRenderScript(TimelineSpec parent, PipelineTask task,
                                               Map<String, String> priorArtifacts) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", parent.id() + "-" + task.taskId());
        payload.put("parentTimelineId", parent.id());
        payload.put("externalRenderTask", task.parameters());
        payload.put("priorArtifacts", priorArtifacts);
        if (parent.outputSpec() != null) {
            payload.put("outputSpec", parent.outputSpec());
        }
        payload.put("tracks", parent.tracks());
        return MAPPER.writeValueAsString(payload);
    }

    private TimelineSpec enrichTimelineWithExternalArtifacts(TimelineSpec timeline,
                                                               Map<String, String> artifacts) {
        Map<String, String> meta = new LinkedHashMap<>(timeline.metadata() != null ? timeline.metadata() : Map.of());
        try {
            meta.put("platform.externalArtifacts", MAPPER.writeValueAsString(artifacts));
        } catch (Exception ignored) {
            meta.put("platform.externalArtifacts", artifacts.toString());
        }
        return new TimelineSpec(
                timeline.id(),
                timeline.name(),
                timeline.description(),
                timeline.tracks(),
                timeline.textOverlays(),
                timeline.outputSpec(),
                timeline.computeDuration(),
                meta);
    }

    private String serializeTimeline(TimelineSpec spec) {
        try {
            return MAPPER.writeValueAsString(spec);
        } catch (Exception e) {
            log.warn("Timeline serialization failed: {}", e.getMessage());
            return "{}";
        }
    }

    private void publishMezzanineCacheIndex(PipelineExecutionPlan plan,
                                           String tenantId,
                                           Map<String, String> stageArtifacts,
                                           Map<String, Object> executionState) {
        if (mezzanineCachePublisher == null || stageArtifacts == null) {
            return;
        }
        Optional<PipelineTask> composeTask = MezzanineCachePublisher.findComposeTask(plan.tasks());
        if (composeTask.isEmpty()) {
            return;
        }
        String localUri = stageArtifacts.get(composeTask.get().taskId());
        if (localUri == null || localUri.isBlank()) {
            return;
        }
        String remoteUri = localUri;
        String contentHash = null;
        if (segmentArtifactUploadService.isPresent()) {
            SegmentArtifactUploadService upload = segmentArtifactUploadService.get();
            remoteUri = upload.uploadMezzanineArtifact(
                            tenantId, composeTask.get().cacheKey(), composeTask.get().taskId(), localUri)
                    .orElse(localUri);
            contentHash = upload.computeContentHash(localUri).orElse(null);
        }
        mezzanineCachePublisher.publish(tenantId, composeTask.get(), localUri, remoteUri, contentHash)
                .ifPresent(entry -> executionState.put("mezzanineCacheIndex", entry));
        executionState.put("finalComposeUri", remoteUri);
    }

    private void publishSegmentCacheIndex(String timelineJson,
                                          String tenantId,
                                          Map<String, String> segmentArtifacts,
                                          Map<String, Object> executionState) {
        if (segmentTimelinePlanner == null || segmentCachePublisher == null || segmentArtifacts.isEmpty()) {
            return;
        }
        segmentTimelinePlanner.planFromTimelineJson(timelineJson).ifPresent(plan -> {
            Map<String, Map<String, String>> index;
            if (segmentArtifactUploadService.isPresent()
                    && segmentArtifactUploadService.get().isUploadEnabled()) {
                index = segmentArtifactUploadService.get().publishWithUpload(
                        tenantId, plan.segments(), segmentArtifacts, segmentCachePublisher);
            } else {
                index = segmentCachePublisher.publish(tenantId, plan.segments(), segmentArtifacts);
            }
            if (!index.isEmpty()) {
                executionState.put("segmentCacheIndex", index);
                Map<String, String> merged = new LinkedHashMap<>(segmentArtifacts);
                index.forEach((cacheKey, row) -> {
                    String segmentId = row.get("segmentId");
                    String remote = row.get("remoteUri");
                    if (segmentId != null && remote != null && !remote.isBlank()) {
                        merged.put(segmentId, remote);
                    }
                });
                executionState.put("segmentArtifacts", merged);
            }
        });
    }

    private static String tenantIdFromTimeline(TimelineSpec timeline) {
        if (timeline.metadata() != null) {
            String tenant = timeline.metadata().get("platform.tenantId");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        return "default";
    }

    public record DagTaskResult(
            String taskId,
            boolean success,
            String storageUri,
            String artifactId,
            String errorMessage) {

        static DagTaskResult success(String taskId, String storageUri, String artifactId) {
            return new DagTaskResult(taskId, true, storageUri, artifactId, null);
        }

        static DagTaskResult failed(String taskId, String error) {
            return new DagTaskResult(taskId, false, null, null, error);
        }

        static DagTaskResult reused(String taskId, String storageUri) {
            return new DagTaskResult(taskId, true, storageUri, "reused", null);
        }
    }

    public record DagExecutionResult(
            boolean success,
            PipelineExecutionPlan plan,
            List<DagTaskResult> externalTaskResults,
            MultiProviderPipelineService.PipelineResult pipelineResult,
            String finalStorageUri,
            String timelineJson,
            String errorMessage) {

        public static DagExecutionResult success(PipelineExecutionPlan plan,
                                                 List<DagTaskResult> externalResults,
                                                 MultiProviderPipelineService.PipelineResult pipeline,
                                                 String storageUri,
                                                 String timelineJson) {
            return new DagExecutionResult(true, plan, externalResults, pipeline, storageUri, timelineJson, null);
        }

        public static DagExecutionResult failed(PipelineExecutionPlan plan,
                                                List<DagTaskResult> externalResults,
                                                String error) {
            return new DagExecutionResult(false, plan, externalResults, null, null, null, error);
        }
    }
}
