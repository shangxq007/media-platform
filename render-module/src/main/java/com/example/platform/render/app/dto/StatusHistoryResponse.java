package com.example.platform.render.app.dto;

import java.time.OffsetDateTime;

public record StatusHistoryResponse(
        String id,
        String jobId,
        String fromStatus,
        String toStatus,
        String reason,
        String errorCode,
        OffsetDateTime occurredAt
) {}
