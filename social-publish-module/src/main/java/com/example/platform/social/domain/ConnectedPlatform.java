package com.example.platform.social.domain;

import java.time.Instant;

public record ConnectedPlatform(
        String id,
        String tenantId,
        String userId,
        String platformType,
        String platformUserId,
        String platformUsername,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
