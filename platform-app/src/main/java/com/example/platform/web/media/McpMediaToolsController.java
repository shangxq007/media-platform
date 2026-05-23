package com.example.platform.web.media;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.ClipColorProbeService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.NleLayerCatalogService;
import com.example.platform.render.app.TimelineColorMetadataService;
import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.app.timeline.IncrementalPlanExplainer;
import com.example.platform.render.app.timeline.IncrementalRenderPlanService;
import com.example.platform.render.app.timeline.RenderImpactAnalyzer;
import com.example.platform.render.app.timeline.TimelineCanonicalizer;
import com.example.platform.render.app.timeline.TimelineSemanticDiffService;
import com.example.platform.render.app.timeline.InternalTimelineAdapter;
import com.example.platform.render.app.timeline.InternalTimelineJson;
import com.example.platform.render.app.timeline.InternalTimelineWriter;
import com.example.platform.render.app.timeline.SegmentPlanFilter;
import com.example.platform.render.app.timeline.TimelineSpecResolver;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import com.example.platform.render.domain.timeline.internal.RenderImpactResult;
import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import com.example.platform.render.domain.timeline.internal.SemanticDiffResult;
import com.example.platform.render.app.aaf.AafConversionService;
import com.example.platform.render.app.TimelineValidationService;
import com.example.platform.render.domain.timeline.standards.AafTimelineAdapter;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.infrastructure.ColorProbeMetadata;
import com.example.platform.render.domain.timeline.OpenTimelineioAdapter;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineValidationResult;
import com.example.platform.render.domain.timeline.standards.EdlTimelineAdapter;
import com.example.platform.render.domain.timeline.standards.FcpXmlTimelineAdapter;
import com.example.platform.render.domain.timeline.standards.SrtSubtitleAdapter;
import com.example.platform.render.domain.timeline.standards.WebVttSubtitleAdapter;
import com.example.platform.render.infrastructure.MediaProbeResult;
import com.example.platform.render.infrastructure.MediaProbeService;
import com.example.platform.render.infrastructure.bento4.Bento4PackagingProvider;
import com.example.platform.render.infrastructure.gpac.GPACPackagingProvider;
import com.example.platform.render.infrastructure.gpac.PackagingDrmProfile;
import com.example.platform.render.infrastructure.gpac.PackagingRequest;
import com.example.platform.render.infrastructure.gpac.PackagingResult;
import com.example.platform.render.infrastructure.shaka.ShakaPackagingProvider;
import com.example.platform.web.CallerContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP+ fine-grained media tools (whitelist; no arbitrary shell).
 */
@RestController
@RequestMapping({"/api/v1/media/tools", "/api/v1/mcp/media/tools"})
@Tag(name = "MCP Media Tools", description = "细粒度 MCP 媒体工具")
public class McpMediaToolsController {

    private static final Logger log = LoggerFactory.getLogger(McpMediaToolsController.class);

    private final MediaProbeService mediaProbeService;
    private final TimelineValidationService timelineValidationService;
    private final TimelineScriptParser timelineScriptParser;
    private final RenderPlannerService renderPlannerService;
    private final TimelinePatchService timelinePatchService;
    private final TimelineColorMetadataService timelineColorMetadataService;
    private final PipelinePlanPersistenceService pipelinePlanPersistence;
    private final ClipColorProbeService clipColorProbeService;
    private final AafConversionService aafConversionService;
    private final NleLayerCatalogService nleLayerCatalogService;
    private final Optional<GPACPackagingProvider> gpacPackaging;
    private final Optional<Bento4PackagingProvider> bento4Packaging;
    private final Optional<ShakaPackagingProvider> shakaPackaging;
    private final TimelineCanonicalizer timelineCanonicalizer;
    private final TimelineSemanticDiffService timelineSemanticDiffService;
    private final RenderImpactAnalyzer renderImpactAnalyzer;
    private final IncrementalPlanExplainer incrementalPlanExplainer;
    private final IncrementalRenderPlanService incrementalRenderPlanService;
    private final InternalTimelineAdapter internalTimelineAdapter;
    private final InternalTimelineWriter internalTimelineWriter;
    private final TimelineSpecResolver timelineSpecResolver;
    private final Optional<RenderOrchestratorPort> renderOrchestratorPort;
    private final TimelineSnapshotService timelineSnapshotService;
    private final SegmentPlanFilter segmentPlanFilter;

    public McpMediaToolsController(MediaProbeService mediaProbeService,
                                   TimelineValidationService timelineValidationService,
                                   TimelineScriptParser timelineScriptParser,
                                   RenderPlannerService renderPlannerService,
                                   TimelinePatchService timelinePatchService,
                                   TimelineColorMetadataService timelineColorMetadataService,
                                   PipelinePlanPersistenceService pipelinePlanPersistence,
                                   ClipColorProbeService clipColorProbeService,
                                   AafConversionService aafConversionService,
                                   NleLayerCatalogService nleLayerCatalogService,
                                   Optional<GPACPackagingProvider> gpacPackaging,
                                   Optional<Bento4PackagingProvider> bento4Packaging,
                                   Optional<ShakaPackagingProvider> shakaPackaging,
                                   TimelineCanonicalizer timelineCanonicalizer,
                                   TimelineSemanticDiffService timelineSemanticDiffService,
                                   RenderImpactAnalyzer renderImpactAnalyzer,
                                   IncrementalPlanExplainer incrementalPlanExplainer,
                                   IncrementalRenderPlanService incrementalRenderPlanService,
                                   InternalTimelineAdapter internalTimelineAdapter,
                                   InternalTimelineWriter internalTimelineWriter,
                                   TimelineSpecResolver timelineSpecResolver,
                                   Optional<RenderOrchestratorPort> renderOrchestratorPort,
                                   TimelineSnapshotService timelineSnapshotService,
                                   SegmentPlanFilter segmentPlanFilter) {
        this.mediaProbeService = mediaProbeService;
        this.timelineValidationService = timelineValidationService;
        this.timelineScriptParser = timelineScriptParser;
        this.renderPlannerService = renderPlannerService;
        this.timelinePatchService = timelinePatchService;
        this.timelineColorMetadataService = timelineColorMetadataService;
        this.pipelinePlanPersistence = pipelinePlanPersistence;
        this.clipColorProbeService = clipColorProbeService;
        this.aafConversionService = aafConversionService;
        this.nleLayerCatalogService = nleLayerCatalogService;
        this.gpacPackaging = gpacPackaging;
        this.bento4Packaging = bento4Packaging;
        this.shakaPackaging = shakaPackaging;
        this.timelineCanonicalizer = timelineCanonicalizer;
        this.timelineSemanticDiffService = timelineSemanticDiffService;
        this.renderImpactAnalyzer = renderImpactAnalyzer;
        this.incrementalPlanExplainer = incrementalPlanExplainer;
        this.incrementalRenderPlanService = incrementalRenderPlanService;
        this.internalTimelineAdapter = internalTimelineAdapter;
        this.internalTimelineWriter = internalTimelineWriter;
        this.timelineSpecResolver = timelineSpecResolver;
        this.renderOrchestratorPort = renderOrchestratorPort;
        this.timelineSnapshotService = timelineSnapshotService;
        this.segmentPlanFilter = segmentPlanFilter;
    }

    @PostMapping("/render_timeline")
    @Operation(summary = "提交 Internal Timeline 1.0 渲染作业（可选 baseJobId 增量）")
    public ResponseEntity<Map<String, Object>> renderTimeline(
            @RequestBody RenderTimelineRequest request,
            HttpServletRequest httpRequest) {
        if (renderOrchestratorPort.isEmpty()) {
            return ResponseEntity.status(503).body(Map.of("error", "Render orchestrator not available"));
        }
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId is required"));
        }
        if (request.projectId() == null || request.projectId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectId is required"));
        }
        if (request.timelineJson() == null || request.timelineJson().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "timelineJson is required"));
        }
        if (!timelineSpecResolver.isInternalTimelineJson(request.timelineJson())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "timelineJson must be Internal Timeline Schema 1.0"));
        }
        try {
            var canon = timelineCanonicalizer.canonicalize(request.timelineJson());
            String timelineJson = canon.timelineJson();
            String snapshotId = request.timelineSnapshotId();
            if (snapshotId == null || snapshotId.isBlank()) {
                snapshotId = timelineSnapshotService.save(
                        request.projectId(), request.tenantId(), timelineJson, "1.0");
            }
            String profile = request.profile() != null && !request.profile().isBlank()
                    ? request.profile() : "default_1080p";
            SubmitRenderJobRequest submit = new SubmitRenderJobRequest(
                    request.tenantId(),
                    request.projectId(),
                    timelineJson,
                    profile,
                    snapshotId,
                    request.baseJobId(),
                    null,
                    null,
                    null,
                    null);
            String jobId = renderOrchestratorPort.get().submitRenderJob(submit);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jobId", jobId);
            body.put("status", "QUEUED");
            body.put("timelineSnapshotId", snapshotId);
            body.put("schemaVersion", InternalTimelineJson.SCHEMA_V1);
            body.put("incremental", request.baseJobId() != null && !request.baseJobId().isBlank());
            body.put("source", resolveSource(httpRequest));
            return ResponseEntity.accepted().body(body);
        } catch (Exception e) {
            log.warn("render_timeline failed: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/render_segment")
    @Operation(summary = "局部段渲染：预览增量计划或提交作业（segmentIds + 可选 baseJobId）")
    public ResponseEntity<Map<String, Object>> renderSegment(
            @RequestBody RenderSegmentRequest request,
            HttpServletRequest httpRequest) {
        if (request.timelineJson() == null || request.timelineJson().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "timelineJson is required"));
        }
        if (request.segmentIds() == null || request.segmentIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "segmentIds is required"));
        }
        try {
            var canon = timelineCanonicalizer.canonicalize(request.timelineJson());
            String timelineJson = canon.timelineJson();
            Map<String, String> segmentMeta = new LinkedHashMap<>();
            SegmentPlanFilter.embedTargetSegmentIds(segmentMeta, request.segmentIds());
            timelineJson = InternalTimelineJson.mergeMetadata(timelineJson, segmentMeta);
            TimelineSpec spec = internalTimelineAdapter.toSpec(timelineJson)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Internal Timeline 1.0 JSON"));

            if (request.submitJob()) {
                if (renderOrchestratorPort.isEmpty()) {
                    return ResponseEntity.status(503).body(Map.of("error", "Render orchestrator not available"));
                }
                if (request.tenantId() == null || request.projectId() == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "tenantId and projectId required when submitJob=true"));
                }
                String snapshotId = timelineSnapshotService.save(
                        request.projectId(), request.tenantId(), timelineJson, "1.0");
                SubmitRenderJobRequest submit = SubmitRenderJobRequest.segmentRender(
                        request.tenantId(),
                        request.projectId(),
                        snapshotId,
                        request.profile() != null ? request.profile() : "default_1080p",
                        request.baseJobId(),
                        request.segmentIds());
                String jobId = renderOrchestratorPort.get().submitRenderJob(submit);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("jobId", jobId);
                body.put("status", "QUEUED");
                body.put("segmentIds", request.segmentIds());
                body.put("timelineSnapshotId", snapshotId);
                return ResponseEntity.accepted().body(body);
            }

            if (request.baseJobId() == null || request.oldTimelineJson() == null) {
                PipelineExecutionPlan plan = renderPlannerService.generatePlan(
                        spec,
                        request.profile() != null ? request.profile() : "default_1080p",
                        request.tier() != null ? request.tier() : "PRO",
                        request.outputFormat() != null ? request.outputFormat() : "mp4",
                        timelineJson);
                plan = segmentPlanFilter.restrictToTargetSegments(
                        plan, java.util.Set.copyOf(request.segmentIds()), Map.of());
                Map<String, Object> body = pipelinePlanBody(plan);
                body.put("segmentIds", request.segmentIds());
                body.put("matchingTasks", plan.tasks().stream()
                        .filter(t -> request.segmentIds().contains(t.taskId()))
                        .map(t -> t.taskId())
                        .toList());
                return ResponseEntity.ok(body);
            }

            IncrementalRenderPlan plan = incrementalRenderPlanService.generate(
                    timelineJson,
                    request.oldTimelineJson(),
                    request.profile() != null ? request.profile() : "default_1080p",
                    request.tier() != null ? request.tier() : "PRO",
                    request.outputFormat() != null ? request.outputFormat() : "mp4",
                    request.baseJobId(),
                    List.of(),
                    request.tenantId());
            Map<String, Object> body = pipelinePlanBody(plan.pipelinePlan());
            body.put("mode", plan.mode());
            body.put("segmentIds", request.segmentIds());
            body.put("executeTaskIds", plan.executeTaskIds().stream()
                    .filter(id -> request.segmentIds().contains(id) || "final_compose".equals(id))
                    .toList());
            body.put("reuseTaskIds", plan.reuseTaskIds());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/nle_layers")
    @Operation(summary = "L3–L7 NLE 层能力与健康状态")
    public ResponseEntity<Map<String, Object>> nleLayers() {
        return ResponseEntity.ok(nleLayerCatalogService.catalog());
    }

    @PostMapping("/probe")
    @Operation(summary = "探测媒体文件")
    public ResponseEntity<Map<String, Object>> probe(
            @RequestBody ProbeRequest request,
            HttpServletRequest httpRequest) {
        log.info("MCP probe: path={} source={}", request.path(), resolveSource(httpRequest));
        MediaProbeResult result = request.absolute()
                ? mediaProbeService.probeAbsolute(request.jobId(), request.path())
                : mediaProbeService.probe(request.jobId(), request.path());
        Map<String, Object> body = probeBody(result);
        if (request.mergeTimelineMetadata() && request.timelineJson() != null && result.color() != null) {
            body.put("timelineJson", timelineColorMetadataService.mergeProbeMetadata(
                    request.timelineJson(), result.color()));
        }
        if (request.probeTimelineClips() && request.timelineJson() != null) {
            ClipColorProbeService.ClipProbeResult clipResult =
                    clipColorProbeService.probeAndEnrichTimeline(
                            request.timelineJson(), request.jobId() != null ? request.jobId() : "probe");
            body.put("clipProbeSuccess", clipResult.success());
            body.put("clipsProbed", clipResult.clipsProbed());
            body.put("clipProbeWarnings", clipResult.warnings());
            if (clipResult.success() && clipResult.timelineJson() != null) {
                body.put("timelineJson", clipResult.timelineJson());
            }
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/probe_timeline_clips")
    @Operation(summary = "探测时间线各 clip 媒体并写入 assetRef.metadata 色彩信息")
    public ResponseEntity<Map<String, Object>> probeTimelineClips(@RequestBody ProbeTimelineClipsRequest request) {
        ClipColorProbeService.ClipProbeResult result = clipColorProbeService.probeAndEnrichTimeline(
                request.timelineJson(), request.jobId() != null ? request.jobId() : "probe");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("clipsProbed", result.clipsProbed());
        body.put("warnings", result.warnings());
        if (result.success()) {
            body.put("timelineJson", result.timelineJson());
        }
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.unprocessableEntity().body(body);
    }

    @PostMapping("/get_render_plan")
    @Operation(summary = "读取作业持久化的 Pipeline DAG 计划")
    public ResponseEntity<Map<String, Object>> getRenderPlan(@RequestBody GetRenderPlanRequest request) {
        var planOpt = pipelinePlanPersistence.loadPlan(request.jobId());
        if (planOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PipelineExecutionPlan plan = planOpt.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planId", plan.planId());
        body.put("timelineId", plan.timelineId());
        body.put("finalComposer", plan.finalComposer().name().toLowerCase());
        body.put("metadata", plan.metadata());
        body.put("tasks", plan.tasks().stream().map(t -> Map.of(
                "taskId", t.taskId(),
                "name", t.name(),
                "type", t.type().name(),
                "backend", t.backend(),
                "dependsOn", t.dependsOn() != null ? t.dependsOn() : List.of(),
                "cacheKey", t.cacheKey() != null ? t.cacheKey() : "")).toList());
        pipelinePlanPersistence.loadExecutionState(request.jobId())
                .ifPresent(state -> body.put("executionState", state));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/canonicalize_timeline")
    @Operation(summary = "规范化 Internal Timeline 1.0（按 id 排序、剥离 volatile 字段）")
    public ResponseEntity<Map<String, Object>> canonicalizeTimeline(
            @RequestBody TimelineJsonRequest request) {
        try {
            TimelineCanonicalizer.CanonicalizeResult result =
                    timelineCanonicalizer.canonicalize(request.timelineJson());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timelineId", result.timelineId());
            body.put("schemaVersion", result.schemaVersion());
            body.put("revision", result.revision());
            body.put("timelineJson", result.timelineJson());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    @PostMapping("/diff_timelines")
    @Operation(summary = "语义 Diff（按稳定 id，canonicalize 后比较）")
    public ResponseEntity<Map<String, Object>> diffTimelines(@RequestBody DiffTimelinesRequest request) {
        try {
            SemanticDiffResult diff = timelineSemanticDiffService.diff(
                    request.oldTimelineJson(), request.newTimelineJson());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("structurallyEqual", diff.structurallyEqual());
            body.put("hasChanges", diff.hasChanges());
            body.put("oldTimelineId", diff.oldTimelineId());
            body.put("newTimelineId", diff.newTimelineId());
            body.put("oldRevision", diff.oldRevision());
            body.put("newRevision", diff.newRevision());
            body.put("schemaVersion", diff.schemaVersion());
            body.put("changes", diff.changes().stream()
                    .map(c -> Map.of(
                            "type", c.type().name(),
                            "entity", c.entity() != null ? c.entity().key() : "",
                            "summary", c.summary()))
                    .toList());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze_render_impact")
    @Operation(summary = "渲染影响分析（dirtyScopes / 增量任务骨架）")
    public ResponseEntity<Map<String, Object>> analyzeRenderImpact(
            @RequestBody DiffTimelinesRequest request) {
        try {
            SemanticDiffResult diff = timelineSemanticDiffService.diff(
                    request.oldTimelineJson(), request.newTimelineJson());
            RenderImpactResult impact = renderImpactAnalyzer.analyze(diff);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fullReRenderRequired", impact.fullReRenderRequired());
            body.put("dirtyScopes", impact.dirtyScopes().stream().map(Enum::name).toList());
            body.put("dirtyEntities", impact.dirtyEntities().stream().map(e -> e.key()).toList());
            body.put("reusableArtifactHints", impact.reusableArtifactHints());
            body.put("suggestedTasks", impact.suggestedTasks().stream()
                    .map(t -> Map.of(
                            "taskId", t.taskId(),
                            "type", t.type(),
                            "target", t.targetEntityKey(),
                            "dependsOn", t.dependsOn(),
                            "parameters", t.parameters()))
                    .toList());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/explain_incremental_plan")
    @Operation(summary = "增量渲染计划可读解释")
    public ResponseEntity<Map<String, Object>> explainIncrementalPlan(
            @RequestBody DiffTimelinesRequest request) {
        try {
            SemanticDiffResult diff = timelineSemanticDiffService.diff(
                    request.oldTimelineJson(), request.newTimelineJson());
            RenderImpactResult impact = renderImpactAnalyzer.analyze(diff);
            return ResponseEntity.ok(incrementalPlanExplainer.explain(impact));
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/validate_timeline")
    @Operation(summary = "校验 Internal Timeline JSON")
    public ResponseEntity<Map<String, Object>> validateTimeline(
            @RequestBody TimelineJsonRequest request) {
        TimelineValidationResult result = timelineValidationService.validateJson(request.timelineJson());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("errors", result.errors());
        body.put("warnings", result.warnings());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/import_otio")
    @Operation(summary = "OTIO JSON → Internal Timeline Schema 1.0")
    public ResponseEntity<Map<String, Object>> importOtio(@RequestBody OtioJsonRequest request) {
        var imported = OpenTimelineioAdapter.importWithReport(request.otioJson());
        String timelineJson = internalTimelineWriter.toJson(imported.timeline(), imported.extensions());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timelineId", imported.timeline().id());
        body.put("schemaVersion", "1.0");
        body.put("timelineJson", timelineJson);
        body.put("warnings", imported.warnings());
        body.put("finalComposer", imported.extensions().finalComposer().name().toLowerCase());
        body.put("trackCount", imported.timeline().tracks().size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/export_otio")
    @Operation(summary = "Internal Timeline 1.0 → OTIO JSON（交换格式）")
    public ResponseEntity<Map<String, Object>> exportOtio(@RequestBody TimelineJsonRequest request) {
        TimelineSpec spec = internalTimelineAdapter.toSpec(request.timelineJson())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Internal Timeline 1.0 JSON"));
        String otio = OpenTimelineioAdapter.toOtioJson(spec);
        return ResponseEntity.ok(Map.of("otioJson", otio));
    }

    @PostMapping("/generate_render_plan")
    @Operation(summary = "生成 Pipeline Render Plan (DAG)")
    public ResponseEntity<Map<String, Object>> generateRenderPlan(
            @RequestBody GeneratePlanRequest request) {
        TimelineSpec spec = internalTimelineAdapter.toSpec(request.timelineJson())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid Internal Timeline 1.0 JSON (requires schemaVersion 1.0 and composition)"));
        PipelineExecutionPlan plan = renderPlannerService.generatePlan(
                spec,
                request.profile() != null ? request.profile() : "default_1080p",
                request.tier() != null ? request.tier() : "PRO",
                request.outputFormat() != null ? request.outputFormat() : "mp4",
                request.timelineJson());
        return ResponseEntity.ok(pipelinePlanBody(plan));
    }

    @PostMapping("/generate_incremental_render_plan")
    @Operation(summary = "基于语义 Diff 生成增量 Render Plan（含 reuse / skipExecution）")
    public ResponseEntity<Map<String, Object>> generateIncrementalRenderPlan(
            @RequestBody GenerateIncrementalPlanRequest request) {
        try {
            IncrementalRenderPlan plan = incrementalRenderPlanService.generate(
                    request.newTimelineJson(),
                    request.oldTimelineJson(),
                    request.profile() != null ? request.profile() : "default_1080p",
                    request.tier() != null ? request.tier() : "PRO",
                    request.outputFormat() != null ? request.outputFormat() : "mp4",
                    request.baseJobId(),
                    request.reuseArtifacts() != null ? request.reuseArtifacts() : List.of(),
                    request.tenantId());
            Map<String, Object> body = pipelinePlanBody(plan.pipelinePlan());
            body.put("mode", plan.mode());
            body.put("fullReRenderRequired", plan.fullReRenderRequired());
            body.put("baseRevision", plan.baseRevision());
            body.put("targetRevision", plan.targetRevision());
            body.put("executeTaskIds", plan.executeTaskIds());
            body.put("reuseTaskIds", plan.reuseTaskIds());
            body.put("reuse", plan.reuse().stream().map(a -> Map.of(
                    "artifactId", a.artifactId(),
                    "taskId", a.taskId(),
                    "uri", a.uri(),
                    "cacheKey", a.cacheKey() != null ? a.cacheKey() : "")).toList());
            body.put("dirtyScopes", plan.impact().dirtyScopes().stream().map(Enum::name).toList());
            body.put("changeCount", plan.diff().changes().size());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> pipelinePlanBody(PipelineExecutionPlan plan) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planId", plan.planId());
        body.put("timelineId", plan.timelineId());
        body.put("finalComposer", plan.finalComposer().name().toLowerCase());
        body.put("metadata", plan.metadata());
        body.put("tasks", plan.tasks().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.taskId());
            m.put("name", t.name());
            m.put("type", t.type().name());
            m.put("backend", t.backend());
            m.put("dependsOn", t.dependsOn() != null ? t.dependsOn() : List.of());
            m.put("cacheKey", t.cacheKey() != null ? t.cacheKey() : "");
            if (t.parameters() != null && !t.parameters().isEmpty()) {
                m.put("parameters", t.parameters());
            }
            return m;
        }).toList());
        return body;
    }

    @PostMapping("/import_srt")
    @Operation(summary = "SRT → Internal Timeline Schema 1.0（含字幕轨）")
    public ResponseEntity<Map<String, Object>> importSrt(@RequestBody SubtitleTextRequest request) {
        var overlays = SrtSubtitleAdapter.parse(request.content());
        TimelineSpec base = TimelineSpec.create("tl-srt-import", "SRT Import", TimelineOutputSpec.mp4_1080p30());
        double duration = overlays.stream()
                .mapToDouble(o -> o.startTime() + o.duration())
                .max()
                .orElse(0);
        TimelineSpec spec = new TimelineSpec(
                base.id(),
                base.name(),
                base.description(),
                base.tracks(),
                overlays,
                base.outputSpec(),
                duration,
                Map.of("platform.import.source", "srt"));
        Map<String, Object> body = internalTimelineBody(spec);
        body.put("overlayCount", overlays.size());
        body.put("overlays", overlays);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/export_webvtt")
    @Operation(summary = "Internal Timeline 1.0 / 遗留 OTIO → WebVTT")
    public ResponseEntity<Map<String, Object>> exportWebVtt(@RequestBody TimelineJsonRequest request) {
        TimelineSpec spec = timelineSpecResolver.resolve(request.timelineJson())
                .orElseThrow(() -> new IllegalArgumentException("Invalid timeline JSON"));
        String vtt = WebVttSubtitleAdapter.toWebVtt(
                spec.textOverlays() != null ? spec.textOverlays() : List.of(),
                request.language() != null ? request.language() : "en");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("webVtt", vtt);
        body.put("schemaVersion", timelineSpecResolver.isInternalTimelineJson(request.timelineJson())
                ? InternalTimelineJson.SCHEMA_V1 : "legacy");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/import_edl")
    @Operation(summary = "EDL → Internal Timeline Schema 1.0")
    public ResponseEntity<Map<String, Object>> importEdl(@RequestBody EdlImportRequest request) {
        TimelineSpec spec = EdlTimelineAdapter.parse(request.edlContent(), request.defaultMediaUri());
        String timelineJson = internalTimelineWriter.toJson(spec);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timelineId", spec.id());
        body.put("schemaVersion", "1.0");
        body.put("timelineJson", timelineJson);
        body.put("duration", spec.computeDuration());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/patch_timeline")
    @Operation(summary = "JSON Patch 风格修改时间线")
    public ResponseEntity<Map<String, Object>> patchTimeline(@RequestBody PatchTimelineRequest request) {
        List<TimelinePatchService.PatchOperation> ops = request.operations().stream()
                .map(o -> new TimelinePatchService.PatchOperation(o.op(), o.path(), o.value()))
                .toList();
        TimelinePatchService.PatchResult result =
                timelinePatchService.applyPatch(request.timelineJson(), ops);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("appliedOps", result.appliedOps());
        body.put("errors", result.errors());
        body.put("warnings", result.warnings());
        if (result.success()) {
            body.put("timelineJson", result.timelineJson());
        }
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.unprocessableEntity().body(body);
    }

    @PostMapping("/import_aaf")
    @Operation(summary = "AAF 导入（JSON/XML manifest、Worker 队列或二进制占位）")
    public ResponseEntity<Map<String, Object>> importAaf(@RequestBody AafImportRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.manifestContent() != null && !request.manifestContent().isBlank()) {
            TimelineSpec spec = AafTimelineAdapter.importFromSource(
                    request.aafPath(), request.manifestContent(), request.defaultMediaUri());
            body.putAll(internalTimelineBody(spec));
            body.put("status", spec.metadata().getOrDefault("platform.import.status", "UNKNOWN"));
            body.put("clipCount", spec.tracks().isEmpty() ? 0 : spec.tracks().get(0).clips().size());
            return ResponseEntity.ok(body);
        }
        if (request.aafPath() != null && !request.aafPath().isBlank()
                && request.queueConversion() && aafConversionService.canAcceptMore()) {
            String conversionId = aafConversionService.enqueue(
                    request.aafPath(), request.defaultMediaUri(), request.tenantId());
            body.put("conversionId", conversionId);
            body.put("status", "QUEUED");
            body.put("warnings", List.of("AAF binary queued for worker conversion"));
            return ResponseEntity.accepted().body(body);
        }
        TimelineSpec spec = AafTimelineAdapter.importFromSource(
                request.aafPath(), null, request.defaultMediaUri());
        body.putAll(internalTimelineBody(spec));
        body.put("status", spec.metadata().getOrDefault("platform.import.status", "UNKNOWN"));
        body.put("clipCount", spec.tracks().isEmpty() ? 0 : spec.tracks().get(0).clips().size());
        if ("PLACEHOLDER_REQUIRES_WORKER".equals(body.get("status"))) {
            body.put("warnings", List.of("Supply manifestContent or set queueConversion=true"));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/aaf_conversion_status")
    @Operation(summary = "查询 AAF Worker 转换结果")
    public ResponseEntity<Map<String, Object>> aafConversionStatus(@RequestBody AafConversionStatusRequest request) {
        return aafConversionService.getResult(request.conversionId())
                .map(result -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("conversionId", result.conversionId());
                    body.put("success", result.success());
                    body.put("status", result.status());
                    body.put("errorMessage", result.errorMessage());
                    if (result.manifestJson() != null) {
                        body.put("manifestJson", result.manifestJson());
                        TimelineSpec spec = AafTimelineAdapter.importFromSource(
                                null, result.manifestJson(), null);
                        body.putAll(internalTimelineBody(spec));
                        body.put("clipCount", spec.tracks().isEmpty() ? 0 : spec.tracks().get(0).clips().size());
                    }
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import_fcpxml")
    @Operation(summary = "FCPXML → Internal Timeline Schema 1.0")
    public ResponseEntity<Map<String, Object>> importFcpxml(@RequestBody FcpXmlRequest request) {
        TimelineSpec spec = FcpXmlTimelineAdapter.parse(request.fcpxml());
        String timelineJson = internalTimelineWriter.toJson(spec);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timelineId", spec.id());
        body.put("schemaVersion", "1.0");
        body.put("timelineJson", timelineJson);
        body.put("clipCount", spec.tracks().isEmpty() ? 0 : spec.tracks().get(0).clips().size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/package/dash")
    @Operation(summary = "DASH 打包")
    public ResponseEntity<Map<String, Object>> packageDash(
            @RequestBody PackageDashRequest request,
            HttpServletRequest httpRequest) {
        log.info("MCP package_dash: input={} packager={} source={}",
                request.inputUri(), request.packager(), resolveSource(httpRequest));

        Map<String, String> extra = new LinkedHashMap<>();
        if (request.drm() != null && request.drm().enabled()) {
            extra.putAll(request.drm().toExtraParams());
        }
        PackagingRequest packagingRequest = new PackagingRequest(
                request.inputUri(),
                request.outputBase(),
                "dash",
                request.segmentDurationSec(),
                List.of(),
                extra);
        PackagingResult result = routePackager(request.packager()).packageMedia(packagingRequest);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("manifestUri", result.manifestUri());
        body.put("format", result.format());
        body.put("errorMessage", result.errorMessage());
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.unprocessableEntity().body(body);
    }

    private Map<String, Object> probeBody(MediaProbeResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("durationMs", result.durationMs());
        body.put("width", result.width());
        body.put("height", result.height());
        body.put("videoCodec", result.videoCodec());
        body.put("audioCodec", result.audioCodec());
        body.put("errorMessage", result.errorMessage());
        ColorProbeMetadata color = result.color();
        body.put("color", Map.of(
                "space", color.colorSpace(),
                "primaries", color.colorPrimaries(),
                "transfer", color.colorTransfer(),
                "range", color.colorRange(),
                "pixelFormat", color.pixelFormat(),
                "hdr", color.hdr()));
        body.put("timelineColorMetadata", color.toTimelineMetadata());
        return body;
    }

    private com.example.platform.render.infrastructure.gpac.PackagingProvider routePackager(String packager) {
        String key = packager != null ? packager.toLowerCase() : "gpac";
        return switch (key) {
            case "bento4" -> bento4Packaging.orElseThrow(() -> new IllegalStateException("Bento4 not enabled"));
            case "shaka" -> shakaPackaging.orElseThrow(() -> new IllegalStateException("Shaka not enabled"));
            default -> gpacPackaging.orElseThrow(() -> new IllegalStateException("GPAC not enabled"));
        };
    }

    private Map<String, Object> internalTimelineBody(TimelineSpec spec) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timelineId", spec.id());
        body.put("schemaVersion", InternalTimelineJson.SCHEMA_V1);
        body.put("timelineJson", internalTimelineWriter.toJson(spec));
        body.put("duration", spec.computeDuration());
        return body;
    }

    private String resolveSource(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/mcp/") ? CallerContext.SOURCE_MCP : CallerContext.SOURCE_WEB;
    }

    public record ProbeRequest(
            String jobId,
            String path,
            boolean absolute,
            String timelineJson,
            boolean mergeTimelineMetadata,
            boolean probeTimelineClips) {

        public ProbeRequest(String jobId, String path, boolean absolute) {
            this(jobId, path, absolute, null, false, false);
        }
    }

    public record ProbeTimelineClipsRequest(String timelineJson, String jobId) {}

    public record GetRenderPlanRequest(String jobId) {}

    public record PackageDashRequest(
            String inputUri,
            String outputBase,
            int segmentDurationSec,
            String packager,
            PackagingDrmProfile drm) {}

    public record TimelineJsonRequest(String timelineJson, String language) {
        public TimelineJsonRequest(String timelineJson) {
            this(timelineJson, null);
        }
    }

    public record OtioJsonRequest(String otioJson) {}

    public record GeneratePlanRequest(
            String timelineJson,
            String profile,
            String tier,
            String outputFormat) {}

    public record GenerateIncrementalPlanRequest(
            String newTimelineJson,
            String oldTimelineJson,
            String profile,
            String tier,
            String outputFormat,
            String baseJobId,
            List<ReusableArtifact> reuseArtifacts,
            String tenantId) {

        public GenerateIncrementalPlanRequest(
                String newTimelineJson,
                String oldTimelineJson,
                String profile,
                String tier,
                String outputFormat,
                String baseJobId,
                List<ReusableArtifact> reuseArtifacts) {
            this(newTimelineJson, oldTimelineJson, profile, tier, outputFormat,
                    baseJobId, reuseArtifacts, null);
        }
    }

    public record RenderTimelineRequest(
            String tenantId,
            String projectId,
            String timelineJson,
            String timelineSnapshotId,
            String profile,
            String baseJobId) {}

    public record RenderSegmentRequest(
            String tenantId,
            String projectId,
            String timelineJson,
            String oldTimelineJson,
            List<String> segmentIds,
            String profile,
            String tier,
            String outputFormat,
            String baseJobId,
            boolean submitJob) {

        public RenderSegmentRequest(
                String tenantId,
                String projectId,
                String timelineJson,
                String oldTimelineJson,
                List<String> segmentIds,
                String profile,
                String tier,
                String outputFormat,
                String baseJobId) {
            this(tenantId, projectId, timelineJson, oldTimelineJson, segmentIds,
                    profile, tier, outputFormat, baseJobId, false);
        }
    }

    public record SubtitleTextRequest(String content) {}

    public record EdlImportRequest(String edlContent, String defaultMediaUri) {}

    public record FcpXmlRequest(String fcpxml) {}

    public record PatchTimelineRequest(String timelineJson, List<PatchOp> operations) {}

    public record PatchOp(String op, String path, JsonNode value) {}

    public record AafImportRequest(
            String aafPath,
            String manifestContent,
            String defaultMediaUri,
            String tenantId,
            boolean queueConversion) {}

    public record AafConversionStatusRequest(String conversionId) {}

    public record DiffTimelinesRequest(String oldTimelineJson, String newTimelineJson) {}
}
