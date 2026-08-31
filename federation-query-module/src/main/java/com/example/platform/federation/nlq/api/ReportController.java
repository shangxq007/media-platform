package com.example.platform.federation.nlq.api;

import com.example.platform.federation.nlq.api.dto.ReportCreateRequest;
import com.example.platform.federation.nlq.api.dto.ReportUpdateRequest;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.app.ReportDefinitionService;
import com.example.platform.federation.nlq.app.ReportExecutionService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportDefinitionService reportDefinitionService;
    private final ReportExecutionService reportExecutionService;

    public ReportController(ReportDefinitionService reportDefinitionService,
            ReportExecutionService reportExecutionService,
            AdminAuditPublisher auditPublisher) {
        this.reportDefinitionService = reportDefinitionService;
        this.reportExecutionService = reportExecutionService;
    }

    @PostMapping
    public Map<String, Object> createReport(@RequestBody ReportCreateRequest request) {
        throw FailClosedAuthorization.unavailable("report creation");
    }

    @GetMapping
    public Map<String, Object> listReports(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String workspaceId) {
        log.info("ReportController: list reports tenantId={}, workspaceId={}", tenantId, workspaceId);

        String effectiveTenant;
        if (tenantId != null && !tenantId.isBlank()) {
            effectiveTenant = resolveTenantId(tenantId);
        } else {
            effectiveTenant = resolveTenantId(null);
        }

        List<ReportDefinition> reports;
        if (workspaceId != null) {
            reports = reportDefinitionService.listByWorkspace(workspaceId);
        } else {
            reports = reportDefinitionService.listByTenant(effectiveTenant);
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
        throw FailClosedAuthorization.unavailable("report update");
    }

    @PostMapping("/{reportId}/execute")
    public Map<String, Object> executeReport(@PathVariable String reportId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "false") boolean isAdmin) {
        throw FailClosedAuthorization.unavailable("report execution");
    }

    @PostMapping("/{reportId}/archive")
    public Map<String, Object> archiveReport(@PathVariable String reportId) {
        throw FailClosedAuthorization.unavailable("report archival");
    }

    private String resolveTenantId(String requestedTenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        return contextTenant;
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
