package com.example.platform.federation.nlq.api;

import com.example.platform.federation.nlq.api.dto.*;
import com.example.platform.federation.nlq.app.*;
import com.example.platform.federation.nlq.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics/nlq")
public class NaturalLanguageQueryController {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageQueryController.class);

    private final SqlGenerationService sqlGenerationService;
    private final SqlSafetyValidator sqlSafetyValidator;
    private final SqlScopeInjector sqlScopeInjector;
    private final SqlCostEstimator sqlCostEstimator;
    private final QueryExecutionService queryExecutionService;
    private final QueryCatalogService queryCatalogService;
    private final QueryHistoryService queryHistoryService;
    private final QueryAuditService queryAuditService;
    private final ChartSuggestionService chartSuggestionService;
    private final FeatureFlagService featureFlagService;

    public NaturalLanguageQueryController(SqlGenerationService sqlGenerationService,
            SqlSafetyValidator sqlSafetyValidator, SqlScopeInjector sqlScopeInjector,
            SqlCostEstimator sqlCostEstimator, QueryExecutionService queryExecutionService,
            QueryCatalogService queryCatalogService, QueryHistoryService queryHistoryService,
            QueryAuditService queryAuditService, ChartSuggestionService chartSuggestionService,
            FeatureFlagService featureFlagService) {
        this.sqlGenerationService = sqlGenerationService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.sqlScopeInjector = sqlScopeInjector;
        this.sqlCostEstimator = sqlCostEstimator;
        this.queryExecutionService = queryExecutionService;
        this.queryCatalogService = queryCatalogService;
        this.queryHistoryService = queryHistoryService;
        this.queryAuditService = queryAuditService;
        this.chartSuggestionService = chartSuggestionService;
        this.featureFlagService = featureFlagService;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody NlqPreviewRequest request) {
        String tenantId = TenantContext.get();
        String userId = request.userId() != null ? request.userId() : "anonymous";
        log.info("NLQ preview: question='{}', userId={}", request.question(), userId);

        boolean nlqEnabled = featureFlagService.isEnabled("nlq.enabled", userId,
            Map.of("tenantId", tenantId), true);
        if (!nlqEnabled) {
            throw new PlatformException(com.example.platform.federation.nlq.NlqErrorCode.NLQ_DISABLED);
        }

        List<QueryDataset> accessibleDatasets = queryCatalogService.listDatasets(
            userId, tenantId, request.workspaceId(),
            request.roles() != null ? request.roles() : List.of(),
            request.permissions() != null ? request.permissions() : List.of(),
            List.of(), List.of());
        List<String> allowedDatasetKeys = accessibleDatasets.stream()
            .map(QueryDataset::datasetKey).toList();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenant_id", tenantId);
        if (request.workspaceId() != null) context.put("workspace_id", request.workspaceId());
        context.put("user_id", userId);

        SqlDraft draft = sqlGenerationService.generateSql(request.question(), context, allowedDatasetKeys);
        SqlSafetyResult safety = sqlSafetyValidator.validate(draft.sql(), new LinkedHashSet<>(allowedDatasetKeys));
        QueryCostEstimate costEstimate = sqlCostEstimator.estimate(draft.sql());
        String scopedSql = sqlScopeInjector.injectScope(draft.sql(), tenantId,
            request.workspaceId(), userId, false, false);

        String accessDecision = safety.safe() ? "ALLOWED" : "DENIED";
        queryAuditService.auditPreview(userId, tenantId, request.question(),
            draft.datasetKeys(), costEstimate.riskLevel(), accessDecision);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("previewId", draft.draftId());
        response.put("question", request.question());
        response.put("intent", draft.intent());
        response.put("datasets", draft.datasetKeys());
        response.put("sqlDraft", scopedSql);
        response.put("sqlExplanation", draft.explanation());
        response.put("parameters", draft.parameters());
        response.put("safety", Map.of(
            "safe", safety.safe(),
            "violations", safety.violations(),
            "riskLevel", costEstimate.riskLevel(),
            "requiresReview", costEstimate.requiresReview()
        ));
        response.put("accessDecision", accessDecision);
        response.put("riskLevel", costEstimate.riskLevel());
        response.put("requiresConfirmation", sqlCostEstimator.requiresConfirmation(costEstimate));
        response.put("chartSuggestions", draft.chartSuggestions());
        response.put("warnings", safety.violations());
        return response;
    }

    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody NlqExecuteRequest request) {
        String tenantId = TenantContext.get();
        String userId = request.userId() != null ? request.userId() : "anonymous";
        log.info("NLQ execute: sql='{}', userId={}", request.sql().substring(0, Math.min(80, request.sql().length())), userId);

        SqlSafetyResult safety = sqlSafetyValidator.validate(request.sql(), Set.of());
        if (!safety.safe()) {
            queryAuditService.auditAccessDenied(userId, tenantId, "", "SQL_UNSAFE");
            throw new PlatformException(com.example.platform.federation.nlq.NlqErrorCode.NLQ_SQL_UNSAFE);
        }

        QueryCostEstimate costEstimate = sqlCostEstimator.estimate(request.sql());
        if (costEstimate.requiresReview() && !Boolean.TRUE.equals(request.confirmed())) {
            throw new PlatformException(com.example.platform.federation.nlq.NlqErrorCode.NLQ_EXECUTION_REQUIRES_CONFIRMATION);
        }

        int maxRows = sqlCostEstimator.clampLimit(request.maxRows() != null ? request.maxRows() : 100);
        int timeout = sqlCostEstimator.getTimeoutSeconds(costEstimate);

        String scopedSql = sqlScopeInjector.injectScope(request.sql(), tenantId,
            request.workspaceId(), userId, false, false);
        Map<String, Object> scopeParams = sqlScopeInjector.buildScopeParameters(
            tenantId, request.workspaceId(), userId, false, false);

        QueryResult result = queryExecutionService.execute(scopedSql, scopeParams, maxRows, timeout);

        queryHistoryService.record(userId, tenantId, request.workspaceId(),
            request.question(), request.sql(), safety.referencedDatasets(),
            result.rowCount(), result.durationMs(), costEstimate.riskLevel(),
            "SUCCESS", null);
        queryAuditService.auditExecute(userId, tenantId, result.queryId(),
            "scoped", safety.referencedDatasets(), result.rowCount(),
            result.durationMs(), costEstimate.riskLevel(), "SUCCESS");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queryId", result.queryId());
        response.put("columns", result.columns());
        response.put("rows", result.rows());
        response.put("rowCount", result.rowCount());
        response.put("truncated", result.truncated());
        response.put("durationMs", result.durationMs());
        response.put("summary", result.summary());
        response.put("chartSuggestions", result.chartSuggestions());
        response.put("warnings", result.warnings());
        return response;
    }

    @PostMapping("/explain")
    public Map<String, Object> explain(@RequestBody NlqExplainRequest request) {
        String tenantId = TenantContext.get();
        String userId = request.userId() != null ? request.userId() : "anonymous";
        log.info("NLQ explain: question='{}', userId={}", request.question(), userId);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenant_id", tenantId);
        if (request.workspaceId() != null) context.put("workspace_id", request.workspaceId());

        List<QueryDataset> accessibleDatasets = queryCatalogService.listDatasets(
            userId, tenantId, request.workspaceId(),
            request.roles() != null ? request.roles() : List.of(),
            request.permissions() != null ? request.permissions() : List.of(),
            List.of(), List.of());
        List<String> allowedDatasetKeys = accessibleDatasets.stream()
            .map(QueryDataset::datasetKey).toList();

        SqlDraft draft = sqlGenerationService.generateSql(request.question(), context, allowedDatasetKeys);
        queryAuditService.auditExplain(userId, tenantId, request.question(), draft.explanation());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("draftId", draft.draftId());
        response.put("intent", draft.intent());
        response.put("datasets", draft.datasetKeys());
        response.put("sql", draft.sql());
        response.put("explanation", draft.explanation());
        response.put("assumptions", draft.assumptions());
        response.put("confidence", draft.confidence());
        response.put("chartSuggestions", draft.chartSuggestions());
        return response;
    }

    @GetMapping("/datasets")
    public Map<String, Object> listDatasets(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) List<String> roles,
            @RequestParam(required = false) List<String> permissions) {
        String tenantId = TenantContext.get();
        String effectiveUserId = userId != null ? userId : "anonymous";

        List<QueryDataset> datasets = queryCatalogService.listDatasets(
            effectiveUserId, tenantId, workspaceId,
            roles != null ? roles : List.of(),
            permissions != null ? permissions : List.of(),
            List.of(), List.of());

        queryAuditService.auditDatasetListed(effectiveUserId, tenantId, datasets.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasets", datasets);
        response.put("total", datasets.size());
        return response;
    }

    @GetMapping("/datasets/{datasetKey}")
    public Map<String, Object> getDataset(@PathVariable String datasetKey) {
        String tenantId = TenantContext.get();

        QueryDataset dataset = queryCatalogService.getDataset(datasetKey)
            .orElseThrow(() -> new RuntimeException("Dataset not found: " + datasetKey));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataset", dataset);
        return response;
    }

    @PostMapping("/chart-suggestions")
    public Map<String, Object> chartSuggestions(@RequestBody NlqChartSuggestionsRequest request) {
        String tenantId = TenantContext.get();
        String userId = request.userId() != null ? request.userId() : "anonymous";

        List<ChartSuggestion> suggestions = chartSuggestionService.suggest(
            request.columns() != null ? request.columns() : List.of(),
            request.rows() != null ? request.rows() : List.of());

        queryAuditService.auditChartSuggestions(userId, tenantId, suggestions.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("suggestions", suggestions);
        return response;
    }
}
