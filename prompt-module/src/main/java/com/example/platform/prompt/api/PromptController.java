package com.example.platform.prompt.api;

import com.example.platform.prompt.app.PromptSafetyPolicyService;
import com.example.platform.prompt.app.PromptSafetyPolicyService.SafetyPolicyResult;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.*;
import com.example.platform.shared.web.PlatformException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API for prompt template management.
 */
@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptTemplateService templateService;
    private final PromptSafetyPolicyService safetyPolicyService;

    public PromptController(PromptTemplateService templateService,
            PromptSafetyPolicyService safetyPolicyService) {
        this.templateService = templateService;
        this.safetyPolicyService = safetyPolicyService;
    }

    // -------------------------------------------------------------------------
    // Template CRUD
    // -------------------------------------------------------------------------

    @PostMapping("/templates")
    public PromptTemplate createTemplate(@RequestBody CreateTemplateRequest request) {
        return templateService.createTemplate(
                request.name(), request.description(), request.category(),
                request.tags(), request.owner(), request.schemaVersion());
    }

    @GetMapping("/templates")
    public List<PromptTemplate> listTemplates(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return templateService.listTemplatesByStatus(PromptTemplateStatus.valueOf(status.toUpperCase()));
        }
        return templateService.listTemplates();
    }

    @GetMapping("/templates/{templateId}")
    public PromptTemplate getTemplate(@PathVariable String templateId) {
        return templateService.getTemplate(templateId);
    }

    @PutMapping("/templates/{templateId}")
    public PromptTemplate updateTemplate(@PathVariable String templateId,
            @RequestBody UpdateTemplateRequest request) {
        return templateService.updateTemplate(templateId,
                request.name(), request.description(), request.category(), request.tags());
    }

    // -------------------------------------------------------------------------
    // Version Management
    // -------------------------------------------------------------------------

    @GetMapping("/templates/{templateId}/versions")
    public List<PromptTemplateVersion> listVersions(@PathVariable String templateId) {
        return templateService.listVersions(templateId);
    }

    @GetMapping("/templates/{templateId}/versions/{promptVersion}")
    public PromptTemplateVersion getVersion(@PathVariable String templateId,
            @PathVariable String promptVersion) {
        return templateService.listVersions(templateId).stream()
                .filter(v -> v.promptVersion().equals(promptVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + promptVersion));
    }

    @PostMapping("/templates/{templateId}/versions")
    public PromptTemplateVersion createVersion(@PathVariable String templateId,
            @RequestBody CreateVersionRequest request) {
        return templateService.createVersion(templateId, request.templateBody(),
                request.variableSchemaJson(), request.changelog(), request.createdBy());
    }

    @PostMapping("/templates/{templateId}/rollback")
    public PromptTemplateVersion rollback(@PathVariable String templateId,
            @RequestBody RollbackRequest request) {
        return templateService.rollbackToVersion(templateId, request.targetVersion());
    }

    @PostMapping("/templates/{templateId}/deprecate")
    public PromptTemplate deprecateTemplate(@PathVariable String templateId) {
        return templateService.deprecateTemplate(templateId);
    }

    // -------------------------------------------------------------------------
    // Render & Validate
    // -------------------------------------------------------------------------

    @PostMapping("/templates/{templateId}/render")
    public PromptRenderResult render(@PathVariable String templateId,
            @RequestBody RenderRequest request) {
        return templateService.render(templateId, request.promptVersion(),
                request.variables(), request.dryRun());
    }

    @PostMapping("/templates/{templateId}/validate")
    public PromptValidationResult validate(@PathVariable String templateId) {
        return templateService.validateTemplate(templateId);
    }

    @PostMapping("/risk/analyze")
    public RiskAnalysisResponse analyzeRisk(@RequestBody RiskAnalysisRequest request) {
        PromptRiskLevel riskLevel = templateService.analyzeRisk(request.content(), request.variables());
        SafetyPolicyResult safetyResult = safetyPolicyService.evaluate(
                request.content(), request.tenantId(), request.userId(),
                request.environment(), request.category());
        return new RiskAnalysisResponse(riskLevel, safetyResult.action().name(),
                safetyResult.explanation(), safetyResult.secretFindings(),
                safetyResult.commandFindings());
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    @PostMapping("/executions")
    public PromptExecutionRun startExecution(@RequestBody StartExecutionRequest request) {
        return templateService.startExecution(
                request.templateId(), request.promptVersion(),
                request.tenantId(), request.userId(),
                request.modelProvider(), request.modelName(),
                request.inputVariables(), request.relatedPromptFile(),
                request.relatedManifestEntry());
    }

    @GetMapping("/executions")
    public List<PromptExecutionRun> listExecutions(
            @RequestParam(required = false) String templateId) {
        if (templateId != null) {
            return templateService.listExecutions(templateId);
        }
        return templateService.listAllExecutions();
    }

    @GetMapping("/executions/{executionId}")
    public PromptExecutionRun getExecution(@PathVariable String executionId) {
        return templateService.getExecution(executionId);
    }

    @PostMapping("/executions/{executionId}/evaluate")
    public PromptEvaluationResult evaluateExecution(@PathVariable String executionId,
            @RequestBody EvaluateRequest request) {
        return templateService.evaluateExecution(executionId, request.evaluatorUserId(),
                request.acceptanceCriteriaMet(), request.documentationUpdated(),
                request.manifestUpdated(), request.testsPass(),
                request.hasHighRiskChanges(), request.hasHumanReviewItems(),
                request.hasScopeCreep(), request.hasFalseClaims());
    }

    @PostMapping("/executions/{executionId}/mark-reviewed")
    public Map<String, String> markReviewed(@PathVariable String executionId,
            @RequestBody MarkReviewedRequest request) {
        PromptExecutionRun run = templateService.getExecution(executionId);
        PromptExecutionRun reviewed = new PromptExecutionRun(
                run.executionId(), run.templateId(), run.promptVersion(),
                run.tenantId(), run.userId(), run.modelProvider(), run.modelName(),
                run.renderedPromptHash(), run.redactedPromptPreview(),
                run.inputVariablesRedactedJson(), run.outputSummary(),
                PromptExecutionStatus.SUCCEEDED, run.riskLevel(),
                run.tokenEstimate(), run.costEstimate(),
                run.startedAt(), run.finishedAt(), null, null,
                run.relatedPromptFile(), run.relatedManifestEntry());
        templateService.saveExecution(reviewed);
        return Map.of("executionId", executionId, "status", "REVIEWED", "reviewedBy", request.reviewerUserId());
    }

    @PostMapping("/executions/{executionId}/complete")
    public PromptExecutionRun completeExecution(@PathVariable String executionId,
            @RequestBody CompleteExecutionRequest request) {
        return templateService.completeExecution(executionId, request.outputSummary());
    }

    @PostMapping("/executions/{executionId}/fail")
    public PromptExecutionRun failExecution(@PathVariable String executionId,
            @RequestBody FailExecutionRequest request) {
        return templateService.failExecution(executionId, request.errorCode(), request.errorDetails());
    }

    @PostMapping("/templates/{templateId}/archive")
    public PromptTemplate archiveTemplate(@PathVariable String templateId) {
        return templateService.archiveTemplate(templateId);
    }

    // -------------------------------------------------------------------------
    // File/Manifest
    // -------------------------------------------------------------------------

    @PostMapping("/files/scan")
    public PromptTemplateService.PromptFileScanResult scanFiles(@RequestBody ScanFilesRequest request) {
        return templateService.scanPromptFiles(request.fileContents(), request.fileNames());
    }

    @PostMapping("/files/import")
    public PromptTemplate importFile(@RequestBody ImportFileRequest request) {
        return templateService.importPromptFile(request.content(), request.fileName(), request.owner());
    }

    @PostMapping("/manifest/validate")
    public Map<String, Object> validateManifest(@RequestBody Map<String, Object> manifest) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!manifest.containsKey("prompts")) {
            errors.add("Missing 'prompts' key in manifest");
        }

        Object promptsObj = manifest.get("prompts");
        int promptCount = 0;
        if (promptsObj instanceof List<?> prompts) {
            promptCount = prompts.size();
        } else if (promptsObj instanceof Map<?, ?> promptsMap) {
            promptCount = promptsMap.size();
        }

        return Map.of(
                "valid", errors.isEmpty(),
                "errors", errors,
                "warnings", warnings,
                "promptCount", promptCount
        );
    }

    // -------------------------------------------------------------------------
    // Exception Handling
    // -------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        return pd;
    }

    @ExceptionHandler(PlatformException.class)
    public ProblemDetail handlePlatformException(PlatformException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.getErrorCode().status()),
                ex.getLocalizedMessage());
        pd.setTitle(ex.getErrorCode().code());
        if (ex.getDetails() != null) {
            pd.setProperties(Map.of("details", ex.getDetails()));
        }
        return pd;
    }

    // -------------------------------------------------------------------------
    // Request DTOs
    // -------------------------------------------------------------------------

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
            String templateId, String promptVersion,
            String tenantId, String userId,
            String modelProvider, String modelName,
            Map<String, Object> inputVariables,
            String relatedPromptFile, String relatedManifestEntry) {}

    public record EvaluateRequest(
            String evaluatorUserId,
            boolean acceptanceCriteriaMet, boolean documentationUpdated,
            boolean manifestUpdated, boolean testsPass,
            boolean hasHighRiskChanges, boolean hasHumanReviewItems,
            boolean hasScopeCreep, boolean hasFalseClaims) {}

    public record MarkReviewedRequest(String reviewerUserId) {}

    public record CompleteExecutionRequest(String outputSummary) {}

    public record FailExecutionRequest(String errorCode, String errorDetails) {}

    public record ScanFilesRequest(List<String> fileContents, List<String> fileNames) {}

    public record ImportFileRequest(String content, String fileName, String owner) {}
}
