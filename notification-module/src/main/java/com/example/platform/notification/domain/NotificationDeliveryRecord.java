package com.example.platform.notification.domain;

import java.time.OffsetDateTime;

public record NotificationDeliveryRecord(
        String deliveryId,
        String eventKey,
        String tenantId,
        String workspaceId,
        String userId,
        String channelType,
        String provider,
        String status,
        int attempts,
        String payloadRedacted,
        String errorCode,
        String providerMessageId,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt,
        OffsetDateTime failedAt,
        OffsetDateTime nextRetryAt
) {}
