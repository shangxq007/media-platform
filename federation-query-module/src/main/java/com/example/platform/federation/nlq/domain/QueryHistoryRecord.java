package com.example.platform.federation.nlq.domain;

import java.time.Instant;
import java.util.List;

public record QueryHistoryRecord(
    String queryId,
    String userId,
    String tenantId,
    String workspaceId,
    String questionRedacted,
    String sqlHash,
    List<String> datasets,
    int rowCount,
    long durationMs,
    String riskLevel,
    String status,
    String errorCode,
    Instant createdAt
) {}
