package com.example.platform.federation.nlq.app;

import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueryAuditService {

    private static final Logger log = LoggerFactory.getLogger(QueryAuditService.class);

    private static final String CATEGORY_NLQ = "nlq";
    private static final String RESOURCE_TYPE_QUERY = "nlq_query";
    private static final String RESOURCE_TYPE_REPORT = "nlq_report";

    private final AuditPort auditPort;

    public QueryAuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void auditPreview(String userId, String tenantId, String question, List<String> datasets,
            String riskLevel, String accessDecision) {
        auditPort.record("USER", "NLQ_PREVIEW", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, null,
            Map.of("userId", userId, "tenantId", tenantId,
                   "questionLength", question != null ? question.length() : 0,
                   "datasets", datasets, "riskLevel", riskLevel,
                   "accessDecision", accessDecision));
        log.debug("QueryAuditService: audited preview for user={}", userId);
    }

    public void auditExecute(String userId, String tenantId, String queryId, String sqlHash,
            List<String> datasets, int rowCount, long durationMs, String riskLevel, String status) {
        auditPort.record("USER", "NLQ_EXECUTE", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, queryId,
            Map.of("userId", userId, "tenantId", tenantId, "sqlHash", sqlHash,
                   "datasets", datasets, "rowCount", rowCount,
                   "durationMs", durationMs, "riskLevel", riskLevel, "status", status));
        log.debug("QueryAuditService: audited execute for queryId={}", queryId);
    }

    public void auditExplain(String userId, String tenantId, String question, String sqlExplanation) {
        auditPort.record("USER", "NLQ_EXPLAIN", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, null,
            Map.of("userId", userId, "tenantId", tenantId,
                   "questionLength", question != null ? question.length() : 0));
        log.debug("QueryAuditService: audited explain for user={}", userId);
    }

    public void auditChartSuggestions(String userId, String tenantId, int suggestionCount) {
        auditPort.record("USER", "NLQ_CHART_SUGGEST", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, null,
            Map.of("userId", userId, "tenantId", tenantId, "suggestionCount", suggestionCount));
        log.debug("QueryAuditService: audited chart suggestions for user={}", userId);
    }

    public void auditAccessDenied(String userId, String tenantId, String question, String reason) {
        auditPort.record("USER", "NLQ_ACCESS_DENIED", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, null,
            Map.of("userId", userId, "tenantId", tenantId, "reason", reason));
        log.warn("QueryAuditService: audited access denied for user={}, reason={}", userId, reason);
    }

    public void auditReportCreated(String userId, String tenantId, String reportId, String reportName) {
        auditPort.record("USER", "NLQ_REPORT_CREATE", CATEGORY_NLQ, RESOURCE_TYPE_REPORT, reportId,
            Map.of("userId", userId, "tenantId", tenantId, "reportName", reportName));
        log.debug("QueryAuditService: audited report creation for reportId={}", reportId);
    }

    public void auditReportExecuted(String userId, String tenantId, String reportId, long durationMs) {
        auditPort.record("USER", "NLQ_REPORT_EXECUTE", CATEGORY_NLQ, RESOURCE_TYPE_REPORT, reportId,
            Map.of("userId", userId, "tenantId", tenantId, "durationMs", durationMs));
        log.debug("QueryAuditService: audited report execution for reportId={}", reportId);
    }

    public void auditReportArchived(String userId, String tenantId, String reportId) {
        auditPort.record("USER", "NLQ_REPORT_ARCHIVE", CATEGORY_NLQ, RESOURCE_TYPE_REPORT, reportId,
            Map.of("userId", userId, "tenantId", tenantId));
        log.debug("QueryAuditService: audited report archive for reportId={}", reportId);
    }

    public void auditDatasetListed(String userId, String tenantId, int datasetCount) {
        auditPort.record("USER", "NLQ_DATASET_LIST", CATEGORY_NLQ, RESOURCE_TYPE_QUERY, null,
            Map.of("userId", userId, "tenantId", tenantId, "datasetCount", datasetCount));
        log.debug("QueryAuditService: audited dataset listing for user={}", userId);
    }
}
