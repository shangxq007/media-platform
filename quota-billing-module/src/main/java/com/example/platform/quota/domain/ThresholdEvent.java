package com.example.platform.quota.domain;

import java.time.Instant;

/**
 * @deprecated EUMF-V1: semantic authority MERGED into entitlement-module Quota Authority
 *             (UCUO-ADR-009); physical retirement DEFERRED to PMPR-DDHV1. In-memory engine
 *             retained for existing render consumers this task.
 */
@Deprecated
public record ThresholdEvent(
        String id,
        String quotaBucketId,
        double thresholdPercentage,
        Instant triggeredAt
) {
    public ThresholdEvent {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (quotaBucketId == null || quotaBucketId.isBlank()) throw new IllegalArgumentException("quotaBucketId is required");
        if (thresholdPercentage < 0 || thresholdPercentage > 100) {
            throw new IllegalArgumentException("thresholdPercentage must be between 0 and 100");
        }
        if (triggeredAt == null) throw new IllegalArgumentException("triggeredAt is required");
    }
}
