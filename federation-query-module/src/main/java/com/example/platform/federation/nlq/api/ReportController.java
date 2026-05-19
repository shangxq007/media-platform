package com.example.platform.federation.nlq.api;

import com.example.platform.federation.nlq.api.dto.ReportCreateRequest;
import com.example.platform.federation.nlq.api.dto.ReportUpdateRequest;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import com.example.platform.federation.nlq.app.ReportDefinitionService;
import com.example.platform.federation.nlq.app.ReportExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportDefinitionService reportDefinitionService;
    private final ReportExecutionService reportExecutionService;

    public ReportController(ReportDefinitionService reportDefinitionService,
            ReportExecutionService reportExecutionService) {
        this.reportDefinitionService = reportDefinitionService;
        this.reportExecutionService = reportExecutionService;
    }

    @PostMapping
    public Map<String, Object> createReport(@RequestBody ReportCreateRequest request) {
        log.info("ReportController: create report name='{}'", request.name());

        ReportDefinition report = reportDefinitionService.create(
            request.tenantId(), request.workspaceId(), request.name(),
            request.description(), request.widgets(), request.queryDefinitions(),
            request.createdBy(), request.visibility(), request.schedule());

        return toResponse(report);
    }

    @GetMapping
    public Map<String, Object> listReports(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String workspaceId) {
        log.info("ReportController: list reports tenantId={}, workspaceId={}", tenantId, workspaceId);

        List<ReportDefinition> reports;
        if (workspaceId != null) {
            reports = reportDefinitionService.listByWorkspace(workspaceId);
        } else if (tenantId != null) {
            reports = reportDefinitionService.listByTenant(tenantId);
        } else {
            reports = reportDefinitionService.listAll();
        }

        List<Map<String, Object>> items = reports.stream().map(this::toResponse).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reports", items);
        response.put("total", items.size());
        return response;
    }

    @GetMapping("/{reportId}")
    public Map<String, Object> getReport(@PathVariable String reportId) {
        log.info("ReportController: get reportId={}", reportId);

        ReportDefinition report = reportDefinitionService.getById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found: " + reportId));
        return toResponse(report);
    }

    @PutMapping("/{reportId}")
    public Map<String, Object> updateReport(@PathVariable String reportId,
            @RequestBody ReportUpdateRequest request) {
        log.info("ReportController: update reportId={}", reportId);

        ReportDefinition updated = reportDefinitionService.update(
            reportId, request.name(), request.description(),
            request.widgets(), request.queryDefinitions(),
            request.visibility(), request.schedule())
            .orElseThrow(() -> new RuntimeException("Report not found: " + reportId));
        return toResponse(updated);
    }

    @PostMapping("/{reportId}/execute")
    public Map<String, Object> executeReport(@PathVariable String reportId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "false") boolean isAdmin) {
        log.info("ReportController: execute reportId={}", reportId);

        ReportExecution execution = reportExecutionService.execute(
            reportId, userId, tenantId, workspaceId, isAdmin);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.executionId());
        response.put("reportId", execution.reportId());
        response.put("status", execution.status());
        response.put("rowCount", execution.rowCount());
        response.put("durationMs", execution.durationMs());
        response.put("errorCode", execution.errorCode());
        response.put("createdAt", execution.createdAt().toString());
        return response;
    }

    @PostMapping("/{reportId}/archive")
    public Map<String, Object> archiveReport(@PathVariable String reportId) {
        log.info("ReportController: archive reportId={}", reportId);

        boolean archived = reportDefinitionService.archive(reportId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reportId", reportId);
        response.put("archived", archived);
        return response;
    }

    private Map<String, Object> toResponse(ReportDefinition report) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reportId", report.reportId());
        response.put("tenantId", report.tenantId());
        response.put("workspaceId", report.workspaceId());
        response.put("name", report.name());
        response.put("description", report.description());
        response.put("widgets", report.widgets());
        response.put("queryDefinitions", report.queryDefinitions());
        response.put("createdBy", report.createdBy());
        response.put("visibility", report.visibility());
        response.put("schedule", report.schedule());
        response.put("createdAt", report.createdAt().toString());
        response.put("updatedAt", report.updatedAt().toString());
        response.put("archived", report.archived());
        return response;
    }
}
