package com.example.platform.quota.domain;

import java.time.Instant;

public record UsageRecord(
        String id,
        String quotaBucketId,
        long amount,
        Instant recordedAt,
        String idempotencyKey
) {
    public UsageRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (quotaBucketId == null || quotaBucketId.isBlank()) throw new IllegalArgumentException("quotaBucketId is required");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (recordedAt == null) throw new IllegalArgumentException("recordedAt is required");
    }
}
