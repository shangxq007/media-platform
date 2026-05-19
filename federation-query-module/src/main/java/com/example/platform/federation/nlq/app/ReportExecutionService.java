package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import com.example.platform.federation.nlq.domain.SqlSafetyResult;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReportExecutionService.class);

    private final ReportDefinitionService reportDefinitionService;
    private final QueryExecutionService queryExecutionService;
    private final SqlSafetyValidator sqlSafetyValidator;
    private final SqlScopeInjector sqlScopeInjector;
    private final QueryAuditService queryAuditService;
    private final Map<String, ReportExecution> executionStore = new ConcurrentHashMap<>();

    public ReportExecutionService(ReportDefinitionService reportDefinitionService,
            QueryExecutionService queryExecutionService, SqlSafetyValidator sqlSafetyValidator,
            SqlScopeInjector sqlScopeInjector, QueryAuditService queryAuditService) {
        this.reportDefinitionService = reportDefinitionService;
        this.queryExecutionService = queryExecutionService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.sqlScopeInjector = sqlScopeInjector;
        this.queryAuditService = queryAuditService;
    }

    public ReportExecution execute(String reportId, String userId, String tenantId,
            String workspaceId, boolean isAdmin) {
        String executionId = Ids.newId("rpx");
        long start = System.currentTimeMillis();

        Optional<ReportDefinition> reportOpt = reportDefinitionService.getById(reportId);
        if (reportOpt.isEmpty()) {
            long elapsed = System.currentTimeMillis() - start;
            ReportExecution failed = new ReportExecution(executionId, reportId, "NOT_FOUND", 0, elapsed, "NLQ_REPORT_NOT_FOUND", Instant.now());
            executionStore.put(executionId, failed);
            return failed;
        }

        ReportDefinition report = reportOpt.get();
        if (report.archived()) {
            long elapsed = System.currentTimeMillis() - start;
            ReportExecution failed = new ReportExecution(executionId, reportId, "ARCHIVED", 0, elapsed, "NLQ_REPORT_NOT_FOUND", Instant.now());
            executionStore.put(executionId, failed);
            return failed;
        }

        List<String> queryDefs = report.queryDefinitions();
        if (queryDefs == null || queryDefs.isEmpty()) {
            long elapsed = System.currentTimeMillis() - start;
            ReportExecution empty = new ReportExecution(executionId, reportId, "SUCCESS", 0, elapsed, null, Instant.now());
            executionStore.put(executionId, empty);
            return empty;
        }

        int totalRows = 0;
        String lastStatus = "SUCCESS";

        for (String sql : queryDefs) {
            SqlSafetyResult safety = sqlSafetyValidator.validate(sql, Set.of());
            if (!safety.safe()) {
                long elapsed = System.currentTimeMillis() - start;
                ReportExecution failed = new ReportExecution(executionId, reportId, "UNSAFE", 0, elapsed, "NLQ_SQL_UNSAFE", Instant.now());
                executionStore.put(executionId, failed);
                return failed;
            }

            String scopedSql = sqlScopeInjector.injectScope(sql, tenantId, workspaceId,
                userId, isAdmin, false);
            Map<String, Object> scopeParams = sqlScopeInjector.buildScopeParameters(
                tenantId, workspaceId, userId, isAdmin, false);

            var result = queryExecutionService.execute(scopedSql, scopeParams, 1000, 10);
            totalRows += result.rowCount();

            if (result.warnings() != null && result.warnings().stream().anyMatch(w -> w.contains("failed"))) {
                lastStatus = "FAILED";
                break;
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        ReportExecution execution = new ReportExecution(executionId, reportId, lastStatus, totalRows, durationMs, null, Instant.now());
        executionStore.put(executionId, execution);

        queryAuditService.auditReportExecuted(userId, tenantId, reportId, durationMs);
        log.info("ReportExecutionService: reportId={}, executionId={}, status={}, rows={}, duration={}ms",
            reportId, executionId, lastStatus, totalRows, durationMs);
        return execution;
    }

    public Optional<ReportExecution> getExecution(String executionId) {
        return Optional.ofNullable(executionStore.get(executionId));
    }
}
