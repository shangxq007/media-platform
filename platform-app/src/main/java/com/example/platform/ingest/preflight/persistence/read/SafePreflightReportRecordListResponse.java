package com.example.platform.ingest.preflight.persistence.read;

import java.util.List;

public record SafePreflightReportRecordListResponse(
    String tenantId,
    String projectId,
    List<SafePreflightReportRecordListItem> items,
    int totalCount,
    int limit,
    int offset
) {}
