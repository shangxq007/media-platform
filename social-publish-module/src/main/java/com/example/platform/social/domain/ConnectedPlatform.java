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
        long bindingVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public ConnectedPlatform {
        if (bindingVersion < 1) {
            throw new IllegalArgumentException("bindingVersion must be positive");
        }
    }
}
