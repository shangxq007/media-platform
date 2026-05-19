package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportSchedule;
import com.example.platform.federation.nlq.domain.ReportWidget;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ReportDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ReportDefinitionService.class);

    private final Map<String, ReportDefinition> reportStore = new ConcurrentHashMap<>();
    private final QueryAuditService queryAuditService;

    public ReportDefinitionService(QueryAuditService queryAuditService) {
        this.queryAuditService = queryAuditService;
    }

    public ReportDefinition create(String tenantId, String workspaceId, String name, String description,
            List<ReportWidget> widgets, List<String> queryDefinitions, String createdBy,
            String visibility, ReportSchedule schedule) {
        String reportId = Ids.newId("rpt");
        Instant now = Instant.now();

        ReportDefinition report = new ReportDefinition(
            reportId, tenantId, workspaceId, name, description,
            widgets != null ? widgets : List.of(),
            queryDefinitions != null ? queryDefinitions : List.of(),
            createdBy, visibility, schedule, now, now, false
        );

        reportStore.put(reportId, report);
        queryAuditService.auditReportCreated(createdBy, tenantId, reportId, name);
        log.info("ReportDefinitionService: created reportId={}, name={}", reportId, name);
        return report;
    }

    public Optional<ReportDefinition> getById(String reportId) {
        return Optional.ofNullable(reportStore.get(reportId));
    }

    public List<ReportDefinition> listByTenant(String tenantId) {
        return reportStore.values().stream()
            .filter(r -> tenantId.equals(r.tenantId()))
            .filter(r -> !r.archived())
            .sorted(Comparator.comparing(ReportDefinition::updatedAt).reversed())
            .collect(Collectors.toList());
    }

    public List<ReportDefinition> listByWorkspace(String workspaceId) {
        return reportStore.values().stream()
            .filter(r -> workspaceId.equals(r.workspaceId()))
            .filter(r -> !r.archived())
            .sorted(Comparator.comparing(ReportDefinition::updatedAt).reversed())
            .collect(Collectors.toList());
    }

    public List<ReportDefinition> listAll() {
        return reportStore.values().stream()
            .filter(r -> !r.archived())
            .sorted(Comparator.comparing(ReportDefinition::updatedAt).reversed())
            .collect(Collectors.toList());
    }

    public Optional<ReportDefinition> update(String reportId, String name, String description,
            List<ReportWidget> widgets, List<String> queryDefinitions,
            String visibility, ReportSchedule schedule) {
        ReportDefinition existing = reportStore.get(reportId);
        if (existing == null || existing.archived()) {
            return Optional.empty();
        }

        ReportDefinition updated = new ReportDefinition(
            reportId, existing.tenantId(), existing.workspaceId(),
            name != null ? name : existing.name(),
            description != null ? description : existing.description(),
            widgets != null ? widgets : existing.widgets(),
            queryDefinitions != null ? queryDefinitions : existing.queryDefinitions(),
            existing.createdBy(),
            visibility != null ? visibility : existing.visibility(),
            schedule != null ? schedule : existing.schedule(),
            existing.createdAt(), Instant.now(), false
        );

        reportStore.put(reportId, updated);
        log.info("ReportDefinitionService: updated reportId={}", reportId);
        return Optional.of(updated);
    }

    public boolean archive(String reportId) {
        ReportDefinition existing = reportStore.get(reportId);
        if (existing == null) {
            return false;
        }

        ReportDefinition archived = new ReportDefinition(
            reportId, existing.tenantId(), existing.workspaceId(),
            existing.name(), existing.description(),
            existing.widgets(), existing.queryDefinitions(),
            existing.createdBy(), existing.visibility(), existing.schedule(),
            existing.createdAt(), Instant.now(), true
        );

        reportStore.put(reportId, archived);
        queryAuditService.auditReportArchived(existing.createdBy(), existing.tenantId(), reportId);
        log.info("ReportDefinitionService: archived reportId={}", reportId);
        return true;
    }

    public boolean isArchived(String reportId) {
        ReportDefinition report = reportStore.get(reportId);
        return report != null && report.archived();
    }
}
