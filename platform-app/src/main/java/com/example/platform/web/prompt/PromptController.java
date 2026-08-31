package com.example.platform.web.prompt;

import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.prompt.app.PromptSafetyPolicyService;
import com.example.platform.prompt.app.PromptSafetyPolicyService.SafetyPolicyResult;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.PromptEvaluationResult;
import com.example.platform.prompt.domain.PromptExecutionRun;
import com.example.platform.prompt.domain.PromptExecutionStatus;
import com.example.platform.prompt.domain.PromptRenderResult;
import com.example.platform.prompt.domain.PromptRiskLevel;
import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptTemplateStatus;
import com.example.platform.prompt.domain.PromptTemplateVersion;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.web.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical prompt HTTP boundary.
 *
 * <p>Only the original MCP routes are aliased under {@code /api/mcp/prompts}. The remaining
 * prompt-owned APIs stay under {@code /api/prompts}, so consolidating the controllers does not
 * expand MCP authority to request-controlled tenant, user, reviewer, or owner fields.
 */
@RestController("canonicalPromptController")
@Tag(name = "Prompt API", description = "Prompt template API with explicit Web and MCP boundaries")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final PromptTemplateService templateService;
    private final PromptSafetyPolicyService safetyPolicyService;
    private final IdentityAccessService identityAccessService;
    private final AuditPort auditPort;

    public PromptController(
            PromptTemplateService templateService,
            PromptSafetyPolicyService safetyPolicyService,
            IdentityAccessService identityAccessService,
            AuditPort auditPort) {
        this.templateService = templateService;
        this.safetyPolicyService = safetyPolicyService;
        this.identityAccessService = identityAccessService;
        this.auditPort = auditPort;
    }

    @PostMapping({"/api/prompts/templates", "/api/mcp/prompts/templates"})
    @Operation(summary = "Create prompt template")
    public ResponseEntity<PromptTemplate> createTemplate(
            @RequestBody CreateTemplateRequest request, HttpServletRequest httpRequest) {
        CallerContext caller = buildCallerContext(httpRequest);
        validateTemplateRequest(request, caller);
        auditPromptAction("PROMPT_TEMPLATE_CREATED", request.name(), caller);
        PromptTemplate template = templateService.createTemplate(
                request.name(), request.description(), request.category(), request.tags(),
                caller.userId(), request.schemaVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }

    @GetMapping({"/api/prompts/templates", "/api/mcp/prompts/templates"})
    @Operation(summary = "List prompt templates")
    public List<PromptTemplate> listTemplates(@RequestParam(required = false) String status) {
        if (status != null) {
            return templateService.listTemplatesByStatus(
                    PromptTemplateStatus.valueOf(status.toUpperCase()));
        }
        return templateService.listTemplates();
    }

    @GetMapping({
        "/api/prompts/templates/{templateId}",
        "/api/mcp/prompts/templates/{templateId}"
    })
    @Operation(summary = "Get prompt template")
    public PromptTemplate getTemplate(@PathVariable String templateId) {
        return templateService.getTemplate(templateId);
    }

    @PutMapping("/api/prompts/templates/{templateId}")
    public PromptTemplate updateTemplate(
            @PathVariable String templateId, @RequestBody UpdateTemplateRequest request) {
        return templateService.updateTemplate(
                templateId, request.name(), request.description(), request.category(), request.tags());
    }

    @GetMapping("/api/prompts/templates/{templateId}/versions")
    public List<PromptTemplateVersion> listVersions(@PathVariable String templateId) {
        return templateService.listVersions(templateId);
    }

    @GetMapping("/api/prompts/templates/{templateId}/versions/{promptVersion}")
    public PromptTemplateVersion getVersion(
            @PathVariable String templateId, @PathVariable String promptVersion) {
        return templateService.listVersions(templateId).stream()
                .filter(version -> version.promptVersion().equals(promptVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version not found: " + promptVersion));
    }

    @PostMapping("/api/prompts/templates/{templateId}/versions")
    public PromptTemplateVersion createVersion(
            @PathVariable String templateId, @RequestBody CreateVersionRequest request) {
        return templateService.createVersion(
                templateId, request.templateBody(), request.variableSchemaJson(),
                request.changelog(), request.createdBy());
    }

    @PostMapping("/api/prompts/templates/{templateId}/rollback")
    public PromptTemplateVersion rollback(
            @PathVariable String templateId, @RequestBody RollbackRequest request) {
        return templateService.rollbackToVersion(templateId, request.targetVersion());
    }

    @PostMapping("/api/prompts/templates/{templateId}/deprecate")
    public PromptTemplate deprecateTemplate(@PathVariable String templateId) {
        return templateService.deprecateTemplate(templateId);
    }

    @PostMapping({
        "/api/prompts/templates/{templateId}/render",
        "/api/mcp/prompts/templates/{templateId}/render"
    })
    @Operation(summary = "Render prompt template")
    public PromptRenderResult renderPrompt(
            @PathVariable String templateId,
            @RequestBody RenderRequest request,
            HttpServletRequest httpRequest) {
        validateRenderRequest(request, buildCallerContext(httpRequest));
        return templateService.render(
                templateId, request.promptVersion(), request.variables(), request.dryRun());
    }

    @PostMapping({
        "/api/prompts/templates/{templateId}/validate",
        "/api/mcp/prompts/templates/{templateId}/validate"
    })
    @Operation(summary = "Validate prompt safety")
    public SafetyPolicyResult validatePrompt(
            @PathVariable String templateId, HttpServletRequest httpRequest) {
        CallerContext caller = buildCallerContext(httpRequest);
        PromptTemplate template = templateService.getTemplate(templateId);
        String content = template.currentPromptVersion() != null
                ? template.currentPromptVersion() : "";
        return safetyPolicyService.evaluate(
                content, caller.tenantId(), caller.userId(), "production", template.category());
    }

    @PostMapping("/api/prompts/risk/analyze")
    public RiskAnalysisResponse analyzeRisk(@RequestBody RiskAnalysisRequest request) {
        PromptRiskLevel riskLevel = templateService.analyzeRisk(
                request.content(), request.variables());
        SafetyPolicyResult safetyResult = safetyPolicyService.evaluate(
                request.content(), request.tenantId(), request.userId(),
                request.environment(), request.category());
        return new RiskAnalysisResponse(
                riskLevel, safetyResult.action().name(), safetyResult.explanation(),
                safetyResult.secretFindings(), safetyResult.commandFindings());
    }

    @PostMapping("/api/prompts/executions")
    public PromptExecutionRun startExecution(@RequestBody StartExecutionRequest request) {
        return templateService.startExecution(
                request.templateId(), request.promptVersion(), request.tenantId(), request.userId(),
                request.modelProvider(), request.modelName(), request.inputVariables(),
                request.relatedPromptFile(), request.relatedManifestEntry());
    }

    @GetMapping("/api/prompts/executions")
    public List<PromptExecutionRun> listExecutions(
            @RequestParam(required = false) String templateId) {
        return templateId == null
                ? templateService.listAllExecutions()
                : templateService.listExecutions(templateId);
    }

    @GetMapping("/api/prompts/executions/{executionId}")
    public PromptExecutionRun getExecution(@PathVariable String executionId) {
        return templateService.getExecution(executionId);
    }

    @PostMapping("/api/prompts/executions/{executionId}/evaluate")
    public PromptEvaluationResult evaluateExecution(
            @PathVariable String executionId, @RequestBody EvaluateRequest request) {
        return templateService.evaluateExecution(
                executionId, request.evaluatorUserId(), request.acceptanceCriteriaMet(),
                request.documentationUpdated(), request.manifestUpdated(), request.testsPass(),
                request.hasHighRiskChanges(), request.hasHumanReviewItems(),
                request.hasScopeCreep(), request.hasFalseClaims());
    }

    @PostMapping("/api/prompts/executions/{executionId}/mark-reviewed")
    public Map<String, String> markReviewed(
            @PathVariable String executionId, @RequestBody MarkReviewedRequest request) {
        PromptExecutionRun run = templateService.getExecution(executionId);
        PromptExecutionRun reviewed = new PromptExecutionRun(
                run.executionId(), run.templateId(), run.promptVersion(), run.tenantId(), run.userId(),
                run.modelProvider(), run.modelName(), run.renderedPromptHash(),
                run.redactedPromptPreview(), run.inputVariablesRedactedJson(), run.outputSummary(),
                PromptExecutionStatus.SUCCEEDED, run.riskLevel(), run.tokenEstimate(),
                run.costEstimate(), run.startedAt(), run.finishedAt(), null, null,
                run.relatedPromptFile(), run.relatedManifestEntry());
        templateService.saveExecution(reviewed);
        return Map.of(
                "executionId", executionId,
                "status", "REVIEWED",
                "reviewedBy", request.reviewerUserId());
    }

    @PostMapping("/api/prompts/executions/{executionId}/complete")
    public PromptExecutionRun completeExecution(
            @PathVariable String executionId, @RequestBody CompleteExecutionRequest request) {
        return templateService.completeExecution(executionId, request.outputSummary());
    }

    @PostMapping("/api/prompts/executions/{executionId}/fail")
    public PromptExecutionRun failExecution(
            @PathVariable String executionId, @RequestBody FailExecutionRequest request) {
        return templateService.failExecution(
                executionId, request.errorCode(), request.errorDetails());
    }

    @PostMapping("/api/prompts/templates/{templateId}/archive")
    public PromptTemplate archiveTemplate(@PathVariable String templateId) {
        return templateService.archiveTemplate(templateId);
    }

    @PostMapping("/api/prompts/files/scan")
    public PromptTemplateService.PromptFileScanResult scanFiles(
            @RequestBody ScanFilesRequest request) {
        return templateService.scanPromptFiles(request.fileContents(), request.fileNames());
    }

    @PostMapping("/api/prompts/files/import")
    public PromptTemplate importFile(@RequestBody ImportFileRequest request) {
        return templateService.importPromptFile(
                request.content(), request.fileName(), request.owner());
    }

    @PostMapping("/api/prompts/manifest/validate")
    public Map<String, Object> validateManifest(@RequestBody Map<String, Object> manifest) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!manifest.containsKey("prompts")) {
            errors.add("Missing 'prompts' key in manifest");
        }

        Object promptsValue = manifest.get("prompts");
        int promptCount = 0;
        if (promptsValue instanceof List<?> prompts) {
            promptCount = prompts.size();
        } else if (promptsValue instanceof Map<?, ?> prompts) {
            promptCount = prompts.size();
        }
        return Map.of(
                "valid", errors.isEmpty(),
                "errors", errors,
                "warnings", warnings,
                "promptCount", promptCount);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Bad Request");
        return detail;
    }

    @ExceptionHandler(PlatformException.class)
    public ProblemDetail handlePlatformException(PlatformException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(exception.getErrorCode().status()),
                exception.getLocalizedMessage());
        detail.setTitle(exception.getErrorCode().code());
        if (exception.getDetails() != null) {
            detail.setProperties(Map.of("details", exception.getDetails()));
        }
        return detail;
    }

    private CallerContext buildCallerContext(HttpServletRequest request) {
        String source = request.getRequestURI().startsWith("/api/mcp/")
                ? CallerContext.SOURCE_MCP : CallerContext.SOURCE_WEB;
        String authType = CallerContext.SOURCE_MCP.equals(source)
                ? CallerContext.AUTH_API_KEY : CallerContext.AUTH_JWT;
        return new CallerContext(
                source,
                resolveUserId(request, source),
                resolveTenantId(request, source),
                authType,
                MDC.get("traceId"));
    }

    private String resolveUserId(HttpServletRequest request, String source) {
        if (CallerContext.SOURCE_MCP.equals(source)) {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null) {
                String principal = identityAccessService.principalOf(apiKey);
                if (principal != null) {
                    return principal;
                }
            }
            return "anonymous";
        }
        Object subject = request.getAttribute("jwt.subject");
        return subject == null ? "anonymous" : subject.toString();
    }

    private String resolveTenantId(HttpServletRequest request, String source) {
        if (CallerContext.SOURCE_MCP.equals(source)) {
            String apiKey = request.getHeader("X-API-Key");
            return apiKey == null ? null : identityAccessService.tenantIdOf(apiKey);
        }
        Object tenant = request.getAttribute("jwt.tenantId");
        return tenant == null ? null : tenant.toString();
    }

    private void validateTemplateRequest(CreateTemplateRequest request, CallerContext caller) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Prompt template name must not be blank");
        }
        if (caller.isWeb() && (request.category() == null || request.category().isBlank())) {
            throw new IllegalArgumentException("Web prompt requests must specify category");
        }
    }

    private void validateRenderRequest(RenderRequest request, CallerContext caller) {
        if (request.variables() == null) {
            throw new IllegalArgumentException("Prompt variables must not be null");
        }
        if (caller.isMcp() && request.variables().size() > 100) {
            throw new IllegalArgumentException("MCP prompt variables must not exceed 100 entries");
        }
    }

    private void auditPromptAction(String action, String templateName, CallerContext caller) {
        try {
            auditPort.record(
                    caller.userId(), action, "PROMPT", "prompt_template", templateName,
                    Map.of(
                            "source", caller.source(),
                            "authType", caller.authType(),
                            "tenantId", caller.tenantId() == null ? "unknown" : caller.tenantId(),
                            "traceId", caller.traceId() == null ? "none" : caller.traceId()));
        } catch (Exception exception) {
            log.warn("Audit logging failed: {}", exception.getMessage());
        }
    }

    public record CreateTemplateRequest(
            String name, String description, String category,
            List<String> tags, String owner, String schemaVersion) {}

    public record UpdateTemplateRequest(
            String name, String description, String category, List<String> tags) {}

    public record CreateVersionRequest(
            String templateBody, String variableSchemaJson,
            String changelog, String createdBy) {}

    public record RollbackRequest(String targetVersion) {}

    public record RenderRequest(
            String promptVersion, Map<String, Object> variables, boolean dryRun) {}

    public record RiskAnalysisRequest(
            String content, Map<String, Object> variables,
            String tenantId, String userId, String environment, String category) {}

    public record RiskAnalysisResponse(
            PromptRiskLevel riskLevel, String action, String explanation,
            List<String> secretFindings, List<String> commandFindings) {}

    public record StartExecutionRequest(
            String templateId, String promptVersion, String tenantId, String userId,
            String modelProvider, String modelName, Map<String, Object> inputVariables,
            String relatedPromptFile, String relatedManifestEntry) {}

    public record EvaluateRequest(
            String evaluatorUserId, boolean acceptanceCriteriaMet,
            boolean documentationUpdated, boolean manifestUpdated, boolean testsPass,
            boolean hasHighRiskChanges, boolean hasHumanReviewItems,
            boolean hasScopeCreep, boolean hasFalseClaims) {}

    public record MarkReviewedRequest(String reviewerUserId) {}

    public record CompleteExecutionRequest(String outputSummary) {}

    public record FailExecutionRequest(String errorCode, String errorDetails) {}

    public record ScanFilesRequest(List<String> fileContents, List<String> fileNames) {}

    public record ImportFileRequest(String content, String fileName, String owner) {}
}
