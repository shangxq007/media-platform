package com.example.platform.web.prompt;

import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.app.PromptSafetyPolicyService;
import com.example.platform.prompt.app.PromptSafetyPolicyService.SafetyPolicyResult;
import com.example.platform.prompt.domain.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.web.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("multiPathPromptController")
@RequestMapping({"/api/v1/prompts", "/api/v1/mcp/prompts"})
@Tag(name = "Prompt API", description = "Prompt 模板接口 — Web 端使用 JWT 鉴权，MCP 端使用 API Key 鉴权")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final PromptTemplateService templateService;
    private final PromptSafetyPolicyService safetyPolicyService;
    private final IdentityAccessService identityAccessService;
    private final AuditPort auditPort;

    public PromptController(PromptTemplateService templateService,
                             PromptSafetyPolicyService safetyPolicyService,
                             IdentityAccessService identityAccessService,
                             AuditPort auditPort) {
        this.templateService = templateService;
        this.safetyPolicyService = safetyPolicyService;
        this.identityAccessService = identityAccessService;
        this.auditPort = auditPort;
    }

    @PostMapping("/templates")
    @Operation(summary = "创建 Prompt 模板", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<PromptTemplate> createTemplate(
            @RequestBody CreateTemplateRequest request,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        validateTemplateRequest(request, ctx);
        auditPromptAction("PROMPT_TEMPLATE_CREATED", request.name(), ctx);
        String owner = ctx.isWeb() ? ctx.userId() : (ctx.userId() != null ? ctx.userId() : request.owner());
        PromptTemplate template = templateService.createTemplate(
                request.name(), request.description(), request.category(),
                request.tags(), owner, request.schemaVersion());
        return ResponseEntity.status(201).body(template);
    }

    @GetMapping("/templates")
    @Operation(summary = "列出 Prompt 模板", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<List<PromptTemplate>> listTemplates(
            @RequestParam(required = false) String status,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        if (status != null) {
            return ResponseEntity.ok(templateService.listTemplatesByStatus(
                    PromptTemplateStatus.valueOf(status.toUpperCase())));
        }
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "获取 Prompt 模板", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<PromptTemplate> getTemplate(
            @PathVariable String templateId,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @PostMapping("/templates/{templateId}/render")
    @Operation(summary = "渲染 Prompt", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<PromptRenderResult> renderPrompt(
            @PathVariable String templateId,
            @RequestBody RenderPromptRequest request,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        validateRenderRequest(request, ctx);
        PromptRenderResult result = templateService.render(
                templateId, null, request.variables(), false);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/templates/{templateId}/validate")
    @Operation(summary = "校验 Prompt 安全性", description = "Web 端使用 JWT Bearer Token，MCP 端使用 X-API-Key")
    public ResponseEntity<SafetyPolicyResult> validatePrompt(
            @PathVariable String templateId,
            HttpServletRequest httpReq) {
        CallerContext ctx = buildCallerContext(httpReq);
        PromptTemplate template = templateService.getTemplate(templateId);
        String content = template.currentPromptVersion() != null ? template.currentPromptVersion() : "";
        SafetyPolicyResult result = safetyPolicyService.evaluate(
                content, ctx.tenantId(), ctx.userId(), "production", template.category());
        return ResponseEntity.ok(result);
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

    private void validateTemplateRequest(CreateTemplateRequest request, CallerContext ctx) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (ctx.isWeb()) {
            if (request.category() == null || request.category().isBlank()) {
                throw new IllegalArgumentException("Web 请求必须指定 category");
            }
        }
    }

    private void validateRenderRequest(RenderPromptRequest request, CallerContext ctx) {
        if (request.variables() == null) {
            throw new IllegalArgumentException("variables 不能为 null");
        }
        if (ctx.isMcp() && request.variables().size() > 100) {
            throw new IllegalArgumentException("MCP 请求 variables 数量不能超过 100");
        }
    }

    private void auditPromptAction(String action, String templateName, CallerContext ctx) {
        try {
            auditPort.record(
                    ctx.userId() != null ? ctx.userId() : "anonymous",
                    action,
                    "PROMPT",
                    "prompt_template",
                    templateName,
                    Map.of(
                            "source", ctx.source(),
                            "authType", ctx.authType(),
                            "tenantId", ctx.tenantId() != null ? ctx.tenantId() : "unknown",
                            "traceId", ctx.traceId() != null ? ctx.traceId() : "none"
                    )
            );
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }
    }

    public record CreateTemplateRequest(String name, String description, String category,
                                         List<String> tags, String owner, String schemaVersion) {}
    public record RenderPromptRequest(Map<String, Object> variables) {
        public RenderPromptRequest {
            if (variables == null) {
                variables = Map.of();
            }
        }
    }
}
