package com.example.platform.scheduler.domain;

import java.time.Instant;
import java.util.Objects;

public record ScheduledJobDefinition(
        String id,
        String name,
        String cronExpression,
        boolean enabled,
        int maxRetries,
        Instant createdAt
) {
    public ScheduledJobDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(cronExpression, "cronExpression must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
    }
}
