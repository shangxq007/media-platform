package com.example.platform.social.domain;

import java.time.Instant;

public record PublishSchedule(
        String id,
        String postId,
        String tenantId,
        String userId,
        Instant scheduledAt,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
