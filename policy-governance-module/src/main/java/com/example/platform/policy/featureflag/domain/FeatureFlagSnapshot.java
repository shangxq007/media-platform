package com.example.platform.policy.featureflag.domain;

import java.time.Instant;
import java.util.Map;

/** Immutable flag values captured before durable workflow admission. */
public record FeatureFlagSnapshot(
        String snapshotId,
        String providerRevision,
        String tenantId,
        String workspaceId,
        Instant capturedAt,
        Map<String, FeatureFlagDecision> decisions) {
    public FeatureFlagSnapshot {
        if (snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("snapshotId is required");
        if (providerRevision == null || providerRevision.isBlank()) throw new IllegalArgumentException("providerRevision is required");
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
    }

    public FeatureFlagDecision decision(String flagKey) {
        FeatureFlagDecision decision = decisions.get(flagKey);
        if (decision == null) throw new IllegalArgumentException("Flag is absent from frozen snapshot: " + flagKey);
        return decision;
    }
}
