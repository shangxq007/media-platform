package com.example.platform.notification.domain;

import java.time.OffsetDateTime;

public record NotificationChannelBinding(
        String bindingId,
        String tenantId,
        String workspaceId,
        String userId,
        String channelType,
        String destinationMasked,
        String destinationEncrypted,
        boolean verified,
        String verificationStatus,
        boolean enabled,
        String provider,
        int failureCount,
        String disabledReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastVerifiedAt
) {}
