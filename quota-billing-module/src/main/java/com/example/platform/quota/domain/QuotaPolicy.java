package com.example.platform.quota.domain;

/**
 * @deprecated EUMF-V1: semantic authority MERGED into entitlement-module Quota Authority
 *             (UCUO-ADR-009); physical retirement DEFERRED to PMPR-DDHV1. In-memory engine
 *             retained for existing render consumers this task.
 */
@Deprecated
public record QuotaPolicy(
        String id,
        String name,
        String rules,
        double thresholdPercentage
) {
    public QuotaPolicy {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (thresholdPercentage < 0 || thresholdPercentage > 100) {
            throw new IllegalArgumentException("thresholdPercentage must be between 0 and 100");
        }
    }
}
