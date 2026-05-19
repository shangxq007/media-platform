package com.example.platform.federation.nlq.domain;

import java.time.Instant;

public record ReportExecution(
    String executionId,
    String reportId,
    String status,
    int rowCount,
    long durationMs,
    String errorCode,
    Instant createdAt
) {}
