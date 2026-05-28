package com.example.platform.federation.nlq.api;

import com.example.platform.federation.nlq.api.dto.ReportCreateRequest;
import com.example.platform.federation.nlq.api.dto.ReportUpdateRequest;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import com.example.platform.federation.nlq.app.ReportDefinitionService;
import com.example.platform.federation.nlq.app.ReportExecutionService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AdminAuditPublisher auditPublisher;

    public ReportController(ReportDefinitionService reportDefinitionService,
            ReportExecutionService reportExecutionService,
            AdminAuditPublisher auditPublisher) {
        this.reportDefinitionService = reportDefinitionService;
        this.reportExecutionService = reportExecutionService;
        this.auditPublisher = auditPublisher;
    }

    @PostMapping
    public Map<String, Object> createReport(@RequestBody ReportCreateRequest request) {
        String tenantId = resolveTenantId(request.tenantId());
        log.info("ReportController: create report name='{}' tenant='{}'", request.name(), tenantId);

        ReportDefinition report = reportDefinitionService.create(
            tenantId, request.workspaceId(), request.name(),
            request.description(), request.widgets(), request.queryDefinitions(),
            request.createdBy(), request.visibility(), request.schedule());

        return toResponse(report);
    }

    @GetMapping
    public Map<String, Object> listReports(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String workspaceId,
            HttpServletRequest request) {
        log.info("ReportController: list reports tenantId={}, workspaceId={}", tenantId, workspaceId);

        String effectiveTenant;
        boolean isCrossTenantQuery = false;
        if (tenantId != null && !tenantId.isBlank()) {
            effectiveTenant = resolveTenantIdForAdmin(tenantId, request);
            String contextTenant = com.example.platform.shared.web.TenantContext.get();
            isCrossTenantQuery = contextTenant != null && !contextTenant.equals(tenantId);
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

        if (isCrossTenantQuery) {
            auditPublisher.publish(
                    extractActor(request), extractRoles(request),
                    "ADMIN_CROSS_TENANT_LIST_REPORTS", "report", null, tenantId, "SUCCESS");
        }
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

        String effectiveTenant;
        if (tenantId != null && !tenantId.isBlank()) {
            effectiveTenant = resolveTenantId(tenantId);
        } else {
            effectiveTenant = resolveTenantId(null);
        }

        ReportExecution execution = reportExecutionService.execute(
            reportId, userId, effectiveTenant, workspaceId, isAdmin);

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

    private String resolveTenantIdForAdmin(String requestedTenantId, HttpServletRequest request) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (!contextTenant.equals(requestedTenantId)) {
            if (!hasAdminRole(request)) {
                auditPublisher.publish(
                        extractActor(request), extractRoles(request),
                        "ADMIN_CROSS_TENANT_LIST_REPORTS", "report", null, requestedTenantId, "DENIED");
                throw new SecurityException(
                        "Admin role required to query other tenants' reports");
            }
        }
        return requestedTenantId;
    }

    private static boolean hasAdminRole(HttpServletRequest request) {
        if (request.isUserInRole("ADMIN")) {
            return true;
        }
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if ("ADMIN".equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }

    private static String extractActor(HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        return subject != null && !subject.toString().isBlank() ? subject.toString() : "anonymous";
    }

    private static String extractRoles(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return String.join(",", roles.stream().map(Object::toString).toList());
        } else if (rolesAttr instanceof String rolesStr) {
            return rolesStr;
        }
        return "none";
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
