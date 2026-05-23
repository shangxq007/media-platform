package com.example.platform.delivery.api.dto;

import java.time.OffsetDateTime;

public record AdminDeliveryJobResponse(
        String id,
        String tenantId,
        String projectId,
        String renderJobId,
        String destinationId,
        String status,
        String sourceUri,
        String remoteUri,
        Long bytesTransferred,
        Integer attemptCount,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {}
