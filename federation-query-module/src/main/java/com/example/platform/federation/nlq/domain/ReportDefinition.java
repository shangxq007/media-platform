package com.example.platform.federation.nlq.domain;

import java.time.Instant;
import java.util.List;

public record ReportDefinition(
    String reportId,
    String tenantId,
    String workspaceId,
    String name,
    String description,
    List<ReportWidget> widgets,
    List<String> queryDefinitions,
    String createdBy,
    String visibility,
    ReportSchedule schedule,
    Instant createdAt,
    Instant updatedAt,
    boolean archived
) {}
