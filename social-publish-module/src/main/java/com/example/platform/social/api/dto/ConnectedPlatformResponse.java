package com.example.platform.social.api.dto;

import java.time.Instant;

public record ConnectedPlatformResponse(
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
