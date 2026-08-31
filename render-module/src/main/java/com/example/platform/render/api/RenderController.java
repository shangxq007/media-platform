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
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
    private final RenderOrchestratorPort orchestratorPort;
    private final java.util.List<com.example.platform.storage.domain.BlobStorage> storageProviders;
    private final RenderIncrementalApiService incrementalApiService;
    private final RenderCachePresignService cachePresignService;
    private final RenderCacheCleanupService cacheCleanupService;
    private final AiTimelineEditService aiTimelineEditService;
    private final TimelineConversionService timelineConversionService;
    private final AiTimelineProposalService aiTimelineProposalService;
    private final com.example.platform.render.app.product.ProductRuntimeService productRuntimeService;
    private final com.example.platform.render.infrastructure.storage.StorageReferenceRepository storageReferenceRepository;
    private final com.example.platform.render.app.access.ArtifactAccessService artifactAccessService;

    @org.springframework.beans.factory.annotation.Autowired
    public RenderController(RenderJobService renderJobService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderOrchestratorPort orchestratorPort,
                             java.util.List<com.example.platform.storage.domain.BlobStorage> storageProviders,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderIncrementalApiService incrementalApiService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderCachePresignService cachePresignService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RenderCacheCleanupService cacheCleanupService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiTimelineEditService aiTimelineEditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TimelineConversionService timelineConversionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiTimelineProposalService aiTimelineProposalService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.platform.render.app.product.ProductRuntimeService productRuntimeService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.platform.render.infrastructure.storage.StorageReferenceRepository storageReferenceRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.platform.render.app.access.ArtifactAccessService artifactAccessService) {
        this.renderJobService = renderJobService;
        this.orchestratorPort = orchestratorPort;
        this.storageProviders = storageProviders;
        this.incrementalApiService = incrementalApiService;
        this.cachePresignService = cachePresignService;
        this.cacheCleanupService = cacheCleanupService;
        this.aiTimelineEditService = aiTimelineEditService;
        this.timelineConversionService = timelineConversionService;
        this.aiTimelineProposalService = aiTimelineProposalService;
        this.productRuntimeService = productRuntimeService;
        this.storageReferenceRepository = storageReferenceRepository;
        this.artifactAccessService = artifactAccessService;
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
        throw FailClosedAuthorization.unavailable("render job creation");
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
        throw FailClosedAuthorization.unavailable("incremental render planning");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/submit")
    @Operation(summary = "提交增量渲染作业", description = "支持 baseJobId、targetSegmentIds 与 inline 1.0 JSON")
    public Map<String, String> submitIncrementalRenderJob(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody SubmitRenderJobRequest request) {
        throw FailClosedAuthorization.unavailable("incremental render submission");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render/cache/cleanup")
    @Operation(summary = "清理过期远程 render cache", description = "删除超过 retention-days 的已完成作业远程 cache 对象（需 render.cache.cleanup-enabled=true）")
    public RenderCacheCleanupResponse cleanupExpiredCache(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        throw FailClosedAuthorization.unavailable("render cache cleanup");
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/cache/presign")
    @Operation(summary = "预签名段/终稿 cache 下载 URL", description = "省略 cacheKey 返回全部；cacheKey 须 URL 编码（含冒号）")
    public Object presignCache(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestParam(required = false) String cacheKey) {
        throw FailClosedAuthorization.unavailable("render cache access");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
    public Map<String, String> startRenderJob(@PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        throw FailClosedAuthorization.unavailable("render job execution");
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
        throw FailClosedAuthorization.unavailable("AI timeline edit");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/preview-internal")
    @Operation(summary = "预览：编辑器/遗留 JSON → Internal Timeline 1.0")
    public TimelineInternalPreviewResponse previewInternalTimeline(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody TimelineInternalPreviewRequest request) {
        throw FailClosedAuthorization.unavailable("timeline preview conversion");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/ai-proposals/{proposalId}/adopt")
    @Operation(summary = "采纳 AI 编辑建议（应用 Patch）")
    public AiTimelineEditResponse adoptAiProposal(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String proposalId,
            @Valid @RequestBody AiProposalResolveRequest request) {
        throw FailClosedAuthorization.unavailable("AI timeline proposal adoption");
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/timeline/ai-proposals/{proposalId}/reject")
    @Operation(summary = "拒绝 AI 编辑建议")
    public AiTimelineEditResponse rejectAiProposal(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String proposalId,
            @Valid @RequestBody AiProposalResolveRequest request) {
        throw FailClosedAuthorization.unavailable("AI timeline proposal rejection");
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

    @GetMapping("/render/jobs/{jobId}/artifacts")
    public List<ArtifactInfoResponse> getArtifacts(@PathVariable String jobId) {
        throw FailClosedAuthorization.unavailable("global render artifact read");
    }

    @PostMapping("/render/jobs/{jobId}/cancel")
    public RenderJobResponse cancelJob(@PathVariable String jobId, @RequestParam String tenantId) {
        throw FailClosedAuthorization.unavailable("global render cancellation");
    }

    @GetMapping("/render/jobs/{jobId}/status-history")
    public List<StatusHistoryResponse> getStatusHistory(@PathVariable String jobId, @RequestParam String tenantId) {
        throw FailClosedAuthorization.unavailable("global render status history read");
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

    private void requireArtifactAccess() {
        if (artifactAccessService == null) {
            throw new IllegalStateException("Artifact access service not available");
        }
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator not available");
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
    public Map<String, String> uploadPreviewMedia(@RequestParam("file") MultipartFile file) {
        throw FailClosedAuthorization.unavailable("preview media upload");
    }

    @GetMapping("/render/jobs/{jobId}/artifacts/{artifactId}/content")
    @Operation(summary = "Get artifact content")
    public ResponseEntity<byte[]> getArtifactContent(
            @PathVariable String jobId,
            @PathVariable String artifactId) {
        throw FailClosedAuthorization.unavailable("global render artifact content read");
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/artifacts/{artifactId}/access")
    @Operation(summary = "Get artifact access descriptor with signed download URL (tenant-scoped)",
            description = "Returns an ephemeral access descriptor (signed URL) for downloading the artifact. "
                    + "Verifies job belongs to the specified tenant and project before generating access. "
                    + "The signed URL expires after a configured TTL. Does not expose storage internals.")
    public ResponseEntity<?> getArtifactAccessScoped(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String artifactId) {
        requireArtifactAccess();

        // Authorization: verify job belongs to tenant/project (throws IllegalArgumentException -> 404)
        renderJobService.getByIdAndProject(tenantId, projectId, jobId);

        // Verify artifact belongs to job, then build access response (never calls presigner before auth passes)
        return resolveArtifactAndBuildAccess(jobId, artifactId);
    }

    @GetMapping("/render/jobs/{jobId}/artifacts/{artifactId}/access")
    @Operation(summary = "Get artifact access descriptor with signed download URL",
            description = "Returns an ephemeral access descriptor (signed URL) for downloading the artifact. "
                    + "The signed URL expires after a configured TTL. Does not expose storage internals.")
    public ResponseEntity<?> getArtifactAccess(
            @PathVariable String jobId,
            @PathVariable String artifactId) {
        throw FailClosedAuthorization.unavailable("global render artifact access");
    }

    /**
     * Resolves artifact from orchestrator, verifies it belongs to the job, and builds the access response.
     * Called only after authorization has passed — the presigner is never invoked before auth.
     */
    private ResponseEntity<?> resolveArtifactAndBuildAccess(String jobId, String artifactId) {
        List<ArtifactInfoResponse> artifacts = orchestratorPort.getArtifactsByJob(jobId);
        var artifact = artifacts.stream()
                .filter(a -> a.artifactId().equals(artifactId))
                .findFirst();
        if (artifact.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Parse storageUri: "bucket/objectKey" format
        String storageUri = artifact.get().storageUri();
        if (storageUri == null || storageUri.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "NOT_FOUND", "message", "No storage location for artifact"));
        }
        String[] parts = storageUri.split("/", 2);
        if (parts.length < 2) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ACCESS_FAILED", "message", "Invalid storage reference"));
        }
        String bucket = parts[0];
        String objectKey = parts[1];

        // Derive filename from objectKey (last path segment)
        String filename = objectKey.contains("/")
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                : objectKey;

        // Delegate to ArtifactAccessService (assumes S3/R2 for artifact storage)
        var descriptor = artifactAccessService.createAccessDescriptor(
                "S3", bucket, objectKey, null, filename, null);

        // Map descriptor status to HTTP response
        if (descriptor.accessType() == com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.SIGNED_URL) {
            return ResponseEntity.ok(descriptor);
        }
        if (descriptor.accessType() == com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(descriptor);
        }
        if (descriptor.accessType() == com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.UNSUPPORTED) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(descriptor);
        }
        if (descriptor.accessType() == com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.ACCESS_FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(descriptor);
        }
        if (descriptor.accessType() == com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.NOT_READY) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(descriptor);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(descriptor);
    }
}
