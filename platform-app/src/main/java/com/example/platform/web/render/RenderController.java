package com.example.platform.web.render;

import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.web.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("multiPathRenderController")
@RequestMapping({"/api/v1/render/jobs", "/api/v1/mcp/render/jobs"})
@Tag(name = "Render API", description = "渲染任务接口 — Web 端使用 JWT 鉴权，MCP 端使用 API Key 鉴权")
public class RenderController {

    private static final Logger log = LoggerFactory.getLogger(RenderController.class);

    private final RenderJobService renderJobService;
    private final IdentityAccessService identityAccessService;
    private final AuditPort auditPort;
    private final RenderOrchestratorPort orchestratorPort;

    public RenderController(RenderJobService renderJobService,
                             IdentityAccessService identityAccessService,
                             AuditPort auditPort,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             RenderOrchestratorPort orchestratorPort) {
        this.renderJobService = renderJobService;
        this.identityAccessService = identityAccessService;
        this.auditPort = auditPort;
        this.orchestratorPort = orchestratorPort;
    }

    @PostMapping
    @Operation(summary = "创建渲染任务", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<RenderJobResponse> submitRenderJob(
            @Valid @RequestBody CreateRenderJobRequest request,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        validateRequest(request, ctx);
        auditSubmission(request, ctx);
        RenderJobResponse response = renderJobService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "获取渲染任务", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<RenderJobResponse> getRenderJob(
            @PathVariable String jobId,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        return ResponseEntity.ok(renderJobService.getById(jobId));
    }

    @GetMapping
    @Operation(summary = "列出渲染任务", description = "按当前租户过滤，Web 端使用 JWT，MCP 端使用 API Key")
    public ResponseEntity<List<RenderJobResponse>> listRenderJobs(HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        return ResponseEntity.ok(renderJobService.list());
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "取消渲染任务", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<RenderJobResponse> cancelRenderJob(
            @PathVariable String jobId,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        return ResponseEntity.ok(renderJobService.cancel(jobId, ctx.tenantId()));
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "重试渲染任务", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<RenderJobResponse> retryRenderJob(
            @PathVariable String jobId,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        return ResponseEntity.ok(renderJobService.retry(jobId, ctx.tenantId()));
    }

    @PostMapping("/{jobId}/execute")
    @Operation(summary = "执行渲染任务", description = "加载时间线快照并执行渲染流水线")
    public ResponseEntity<Map<String, String>> executeRenderJob(
            @PathVariable String jobId,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        if (ctx.tenantId() == null || ctx.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator not available");
        }
        orchestratorPort.executeExistingRenderJob(ctx.tenantId(), jobId);
        RenderJobResponse job = renderJobService.getById(jobId);
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", job.status()));
    }

    private CallerContext buildCallerContext(HttpServletRequest req) {
        String path = req.getRequestURI();
        String source = path.startsWith("/api/v1/mcp/") ? CallerContext.SOURCE_MCP : CallerContext.SOURCE_WEB;
        String authType = source.equals(CallerContext.SOURCE_MCP) ? CallerContext.AUTH_API_KEY : CallerContext.AUTH_JWT;
        String userId = getUserId(req, source);
        String tenantId = getTenantId(req, source);
        String traceId = MDC.get("traceId");
        return new CallerContext(source, userId, tenantId, authType, traceId);
    }

    private String getUserId(HttpServletRequest req, String source) {
        if (CallerContext.SOURCE_MCP.equals(source)) {
            String apiKey = req.getHeader("X-API-Key");
            if (apiKey != null) {
                String principal = identityAccessService.principalOf(apiKey);
                if (principal != null) return principal;
            }
            return "anonymous";
        }
        Object subject = req.getAttribute("jwt.subject");
        return subject != null ? subject.toString() : "anonymous";
    }

    private String getTenantId(HttpServletRequest req, String source) {
        if (CallerContext.SOURCE_MCP.equals(source)) {
            String apiKey = req.getHeader("X-API-Key");
            if (apiKey != null) {
                String tenantId = identityAccessService.tenantIdOf(apiKey);
                if (tenantId != null) return tenantId;
            }
            return null;
        }
        Object tenantId = req.getAttribute("jwt.tenantId");
        return tenantId != null ? tenantId.toString() : null;
    }

    private void validateRequest(CreateRenderJobRequest request, CallerContext ctx) {
        if (ctx.isWeb()) {
            if (request.profile() == null || request.profile().isBlank()) {
                throw new IllegalArgumentException("Web 请求必须指定 profile");
            }
        }
        if (request.projectId() == null || request.projectId().isBlank()) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        if (request.timelineSnapshotId() == null || request.timelineSnapshotId().isBlank()) {
            throw new IllegalArgumentException("timelineSnapshotId 不能为空");
        }
    }

    private void auditSubmission(CreateRenderJobRequest request, CallerContext ctx) {
        try {
            auditPort.record(
                    ctx.userId() != null ? ctx.userId() : "anonymous",
                    "RENDER_JOB_SUBMITTED",
                    "RENDER",
                    "render_job",
                    request.projectId(),
                    Map.of(
                            "source", ctx.source(),
                            "authType", ctx.authType(),
                            "tenantId", ctx.tenantId() != null ? ctx.tenantId() : "unknown",
                            "profile", request.profile() != null ? request.profile() : "default",
                            "traceId", ctx.traceId() != null ? ctx.traceId() : "none"
                    )
            );
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }
    }
}
