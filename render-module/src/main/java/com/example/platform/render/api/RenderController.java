package com.example.platform.render.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.platform.render.api.dto.GenerateIncrementalPlanRequest;
import com.example.platform.render.api.dto.IncrementalRenderPlanResponse;
import com.example.platform.render.api.dto.RenderCacheEntryPresignDto;
import com.example.platform.render.api.dto.RenderCacheCleanupResponse;
import com.example.platform.render.api.dto.RenderCachePresignResponseDto;
import com.example.platform.render.api.dto.AiProposalDto;
import com.example.platform.render.api.dto.AiProposalResolveRequest;
import com.example.platform.render.api.dto.AiTimelineEditRequest;
import com.example.platform.render.api.dto.AiTimelineEditResponse;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.dto.TimelineInternalPreviewRequest;
import com.example.platform.render.api.dto.TimelineInternalPreviewResponse;
import com.example.platform.render.app.timeline.AiTimelineEditContext;
import com.example.platform.render.app.timeline.AiTimelineEditService;
import com.example.platform.render.app.timeline.AiTimelineProposalService;
import com.example.platform.render.app.timeline.TimelineConversionService;
import com.example.platform.render.app.cache.RenderCacheCleanupService;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.cache.RenderCachePresignService;
import com.example.platform.render.app.cache.RenderIncrementalApiService;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.request.RenderInitiator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Render Jobs", description = "渲染作业与增量渲染 REST API")
public class RenderController {
    private static final Logger log = LoggerFactory.getLogger(RenderController.class);
    private final RenderJobService renderJobService;
    private final com.example.platform.render.api.context.ExecutionContextQueries executionContexts;
    private final RenderOrchestratorPort orchestratorPort;
    private final RenderIncrementalApiService incrementalApiService;
    private final RenderCachePresignService cachePresignService;
    private final RenderCacheCleanupService cacheCleanupService;
    private final AiTimelineEditService aiTimelineEditService;
    private final TimelineConversionService timelineConversionService;
    private final AiTimelineProposalService aiTimelineProposalService;
    private final com.example.platform.render.app.preview.PreviewMediaUploadService previewUploads;
    private final CanonicalActorResolver canonicalActorResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public RenderController(RenderJobService renderJobService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderOrchestratorPort orchestratorPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderIncrementalApiService incrementalApiService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderCachePresignService cachePresignService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderCacheCleanupService cacheCleanupService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiTimelineEditService aiTimelineEditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TimelineConversionService timelineConversionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiTimelineProposalService aiTimelineProposalService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.platform.render.app.preview.PreviewMediaUploadService previewUploads,
            CanonicalActorResolver canonicalActorResolver, com.example.platform.render.api.context.ExecutionContextQueries executionContexts) {
        this.renderJobService = renderJobService;
        this.executionContexts = executionContexts;
        this.orchestratorPort = orchestratorPort;
        this.incrementalApiService = incrementalApiService;
        this.cachePresignService = cachePresignService;
        this.cacheCleanupService = cacheCleanupService;
        this.aiTimelineEditService = aiTimelineEditService;
        this.timelineConversionService = timelineConversionService;
        this.aiTimelineProposalService = aiTimelineProposalService;
        this.previewUploads = previewUploads;
        this.canonicalActorResolver = canonicalActorResolver;
    }


    @jakarta.annotation.PostConstruct
    public void _diagnosticInit() {
        log.info("=== RENDER CONTROLLER DIAGNOSTIC ===");
        log.info("Class: {}", this.getClass().getName());
        log.info("ClassLoader: {}", this.getClass().getClassLoader());
        log.info("Resource: {}", this.getClass().getResource("RenderController.class"));
        
        java.util.List<String> methodNames = new java.util.ArrayList<>();
        for (var m : this.getClass().getDeclaredMethods()) {
            methodNames.add(m.getName());
        }
        log.info("Declared methods ({}): {}", methodNames.size(), methodNames);
        log.info("Has uploadPreviewMedia: {}", methodNames.contains("uploadPreviewMedia"));
        log.info("Has getArtifactContent: {}", methodNames.contains("getArtifactContent"));
        
        // Check annotations
        for (var m : this.getClass().getDeclaredMethods()) {
            if (m.getName().equals("uploadPreviewMedia") || m.getName().equals("getArtifactContent")) {
                log.info("Method {} annotations: {}", m.getName(), java.util.Arrays.toString(m.getAnnotations()));
            }
        }
        
        // Check resource URL for bytecode hash
        try {
            var url = this.getClass().getResource("RenderController.class");
            if (url != null) {
                var conn = url.openConnection();
                try (var is = conn.getInputStream()) {
                    var bytes = is.readAllBytes();
                    var md = java.security.MessageDigest.getInstance("SHA-256");
                    var hash = java.util.HexFormat.of().formatHex(md.digest(bytes));
                    log.info("RenderController bytecode SHA-256: {}", hash);
                }
            }
        } catch (Exception e) {
            log.warn("Could not compute bytecode hash: {}", e.getMessage());
        }
        
        log.info("=== END DIAGNOSTIC ===");
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped render job endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs")
    public RenderJobResponse createRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateRenderJobRequest request) {
        return renderJobService.createForProject(
                tenantId, projectId, request, requireInitiator(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execution-context")
    public com.example.platform.render.api.context.AcceptedExecutionContext getExecutionContext(
            @PathVariable String tenantId,@PathVariable String projectId,@PathVariable String jobId) {
        renderJobService.getByIdAndProject(tenantId,projectId,jobId);
        return executionContexts.get(tenantId,projectId,jobId);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}")
    public RenderJobResponse getRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return renderJobService.getByIdAndProject(tenantId, projectId, jobId);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs")
    public List<RenderJobResponse> listRenderJobs(@PathVariable String tenantId,
            @PathVariable String projectId) {
        return renderJobService.listByProject(tenantId, projectId);
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/plan")
    @Operation(summary = "预览增量渲染计划", description = "语义 Diff + DAG reuse/execute；baseJobId 须属同一租户/项目")
    public IncrementalRenderPlanResponse previewIncrementalPlan(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody GenerateIncrementalPlanRequest request) throws java.io.IOException {
        requireIncrementalApi();
        return incrementalApiService.previewPlan(tenantId, projectId, request);
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/submit")
    @Operation(summary = "提交增量渲染作业", description = "支持 baseJobId、targetSegmentIds 与 inline 1.0 JSON")
    public Map<String, String> submitIncrementalRenderJob(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody SubmitRenderJobRequest request) {
        if (!tenantId.equals(request.tenantId()) || !projectId.equals(request.projectId())) {
            throw new IllegalArgumentException("Path tenant/project must match request body");
        }
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator is not available");
        }
        String jobId = orchestratorPort.submitRenderJob(request, requireInitiator(tenantId));
        return Map.of("jobId", jobId, "status", "QUEUED");
    }

    private RenderInitiator requireInitiator(String tenantId) {
        RenderInitiator initiator = canonicalActorResolver.resolveCurrentActor()
                .map(RenderInitiator::from)
                .orElseThrow(() -> new IllegalStateException("Authenticated render initiator is required"));
        if (!tenantId.equals(initiator.tenantId())) {
            throw new IllegalArgumentException("Render initiator tenant does not match request tenant");
        }
        return initiator;
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render/cache/cleanup")
    @Operation(summary = "清理过期远程 render cache", description = "删除超过 retention-days 的已完成作业远程 cache 对象（需 render.cache.cleanup-enabled=true）")
    public RenderCacheCleanupResponse cleanupExpiredCache(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        requireCacheCleanup();
        var result = cacheCleanupService.runCleanup(tenantId, projectId);
        return new RenderCacheCleanupResponse(
                result.jobsScanned(), result.objectsDeleted(), result.jobsUpdated());
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/cache/presign")
    @Operation(summary = "预签名段/终稿 cache 下载 URL", description = "省略 cacheKey 返回全部；cacheKey 须 URL 编码（含冒号）")
    public Object presignCache(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestParam(required = false) String cacheKey) {
        requireCachePresign();
        if (cacheKey != null && !cacheKey.isBlank()) {
            return toEntryDto(cachePresignService.presignOne(tenantId, projectId, jobId, cacheKey));
        }
        return toPresignDto(cachePresignService.presignAll(tenantId, projectId, jobId));
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
    public Map<String, String> startRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator is not available");
        }
        renderJobService.getByIdAndProject(tenantId, projectId, jobId);
        String resultJobId = orchestratorPort.executeExistingRenderJob(tenantId, jobId);
        return Map.of("jobId", resultJobId, "status", "STARTED");
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execution")
    public RenderJobResponse getExecution(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return renderJobService.getByIdAndProject(tenantId, projectId, jobId);
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/timeline")
    @Operation(summary = "获取作业 Internal Timeline / ai_script（用于编辑后再渲）")
    public Map<String, String> getJobTimeline(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        renderJobService.getByIdAndProject(tenantId, projectId, jobId);
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator is not available");
        }
        String json = orchestratorPort.loadJobTimelineJson(tenantId, jobId);
        return Map.of("timelineJson", json != null ? json : "");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/ai-edit")
    @Operation(summary = "AI 自然语言编辑 Internal Timeline 1.0（多轮改稿）")
    public AiTimelineEditResponse aiEditTimeline(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody AiTimelineEditRequest request) {
        if (aiTimelineEditService == null) {
            throw new IllegalStateException("AI timeline edit is not available");
        }
        if (!tenantId.equals(request.tenantId()) || !projectId.equals(request.projectId())) {
            throw new IllegalArgumentException("Path tenant/project must match request body");
        }
        boolean humanInTheLoop = Boolean.TRUE.equals(request.humanInTheLoop());
        AiTimelineEditContext ctx = new AiTimelineEditContext(
                tenantId,
                projectId,
                request.editSessionId(),
                request.baseJobId(),
                request.intent(),
                request.conversationId(),
                request.instruction(),
                null,
                humanInTheLoop);
        var result = request.baseJobId() != null && !request.baseJobId().isBlank()
                ? aiTimelineEditService.editFromBaseJob(tenantId, request.baseJobId(), request.instruction(), ctx)
                : aiTimelineEditService.editTimeline(request.baseTimelineJson(), request.instruction(), ctx);
        return toAiTimelineEditResponse(result);
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/preview-internal")
    @Operation(summary = "预览：编辑器/遗留 JSON → Internal Timeline 1.0")
    public TimelineInternalPreviewResponse previewInternalTimeline(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody TimelineInternalPreviewRequest request) {
        if (timelineConversionService == null) {
            throw new IllegalStateException("Timeline conversion is not available");
        }
        var preview = timelineConversionService.preview(request.timelineJson());
        var s = preview.summary();
        return new TimelineInternalPreviewResponse(
                preview.internalTimelineJson(),
                preview.sourceSchema(),
                preview.alreadyInternal(),
                s.sourceTrackOrLayerCount(),
                s.internalTrackOrLayerCount(),
                s.sourceClipCount(),
                s.internalClipCount(),
                s.targetRevision(),
                s.jsonByteDelta());
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/ai-proposals/{proposalId}/adopt")
    @Operation(summary = "采纳 AI 编辑建议（应用 Patch）")
    public AiTimelineEditResponse adoptAiProposal(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String proposalId,
            @Valid @RequestBody AiProposalResolveRequest request) {
        if (aiTimelineProposalService == null) {
            throw new IllegalStateException("AI proposal service is not available");
        }
        var resolved = aiTimelineProposalService.adopt(request.timelineJson(), proposalId);
        // CFRH-I1: legacy AI-adopt revision persistence (recordAiAdoptRevision) removed
        // (DELETE_OBSOLETE_PRODUCT_BEHAVIOR — LOSSLESS_MIGRATION_PROOF = FAIL). The adopt
        // operation retains its separable proposal-resolve semantics; no revision is created.
        return new AiTimelineEditResponse(
                resolved.timelineJson(),
                "platform",
                "proposal-adopt",
                resolved.applied(),
                toProposalDtos(aiTimelineProposalService.listProposals(resolved.timelineJson())),
                null);
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/ai-proposals/{proposalId}/reject")
    @Operation(summary = "拒绝 AI 编辑建议")
    public AiTimelineEditResponse rejectAiProposal(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String proposalId,
            @Valid @RequestBody AiProposalResolveRequest request) {
        if (aiTimelineProposalService == null) {
            throw new IllegalStateException("AI proposal service is not available");
        }
        var resolved = aiTimelineProposalService.reject(request.timelineJson(), proposalId);
        return new AiTimelineEditResponse(
                resolved.timelineJson(),
                "platform",
                "proposal-reject",
                false,
                toProposalDtos(aiTimelineProposalService.listProposals(resolved.timelineJson())),
                null);
    }

    private static AiTimelineEditResponse toAiTimelineEditResponse(AiTimelineEditService.AiTimelineEditResult result) {
        return new AiTimelineEditResponse(
                result.timelineJson(),
                result.provider(),
                result.model(),
                result.appliedPatch(),
                toProposalDtos(result.proposals()),
                result.pendingProposalId());
    }

    private static List<AiProposalDto> toProposalDtos(List<AiTimelineProposalService.AiProposalView> views) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        return views.stream()
                .map(v -> new AiProposalDto(v.id(), v.status(), v.summary(), v.createdAt(), v.operationCount()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // User-context endpoints (tenant resolved from JWT or query param)
    // -------------------------------------------------------------------------

    @PostMapping("/render/jobs/{jobId}/cancel")
    public RenderJobResponse cancelJob(@PathVariable String jobId, @RequestParam String tenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        return renderJobService.cancel(jobId, tenantId);
    }

    @GetMapping("/render/jobs/{jobId}/status-history")
    public List<StatusHistoryResponse> getStatusHistory(@PathVariable String jobId, @RequestParam String tenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        return renderJobService.getStatusHistory(jobId, tenantId);
    }

    private void requireIncrementalApi() {
        if (incrementalApiService == null) {
            throw new IllegalStateException("Incremental render API is not available");
        }
    }

    private void requireCachePresign() {
        if (cachePresignService == null) {
            throw new IllegalStateException("Render cache presign is not available");
        }
    }

    private void requireCacheCleanup() {
        if (cacheCleanupService == null) {
            throw new IllegalStateException("Render cache cleanup is not available");
        }
    }

    private static RenderCachePresignResponseDto toPresignDto(RenderCachePresignService.CachePresignResponse response) {
        List<RenderCacheEntryPresignDto> entries = response.entries().stream()
                .map(RenderController::toEntryDto)
                .toList();
        return new RenderCachePresignResponseDto(response.jobId(), entries);
    }

    private static RenderCacheEntryPresignDto toEntryDto(RenderCachePresignService.CacheEntryPresign entry) {
        return new RenderCacheEntryPresignDto(
                entry.cacheKey(),
                entry.segmentId(),
                entry.taskId(),
                entry.kind(),
                entry.sourceUri(),
                entry.downloadUrl(),
                entry.expiresIn().toSeconds());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Operation Failed");
        return pd;
    }

    // -------------------------------------------------------------------------
    // Preview media upload & artifact content endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/preview/media")
    @Operation(summary = "Upload preview media (dev/preview only)")
    public Map<String, String> uploadPreviewMedia(@RequestParam("file") MultipartFile file,
            @RequestHeader(value="Idempotency-Key",required=false) String requestKey,
            @RequestHeader(value="X-Tenant-ID",required=false) String tenantHint,
            jakarta.servlet.http.HttpServletResponse response) {
        if(tenantHint!=null && !tenantHint.equals(com.example.platform.shared.web.TenantGuard.requireTenantId()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Preview tenant header must match authenticated tenant");
        if(file.isEmpty())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Preview media is empty");
        if (!"video/mp4".equals(file.getContentType())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Only video/mp4 is supported");
        if (file.getSize()>20*1024*1024) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"File too large (max 20MB)");
        if(previewUploads==null)throw new IllegalStateException("Preview upload is not available");
        com.example.platform.render.api.request.PreviewUploadKey key;
        try {key=new com.example.platform.render.api.request.PreviewUploadKey(
                requestKey==null?java.util.UUID.randomUUID().toString():requestKey);}
        catch(IllegalArgumentException invalid){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage(),invalid);}
        response.setHeader("Idempotency-Key",key.value());
        try {
            var result=previewUploads.upload(key,file.getBytes(),file.getContentType());
            return Map.of("mediaId",result.mediaId(),"size",Long.toString(result.size()));
        } catch(java.io.IOException e){throw new IllegalStateException("Failed to read preview media",e);}
    }
}
