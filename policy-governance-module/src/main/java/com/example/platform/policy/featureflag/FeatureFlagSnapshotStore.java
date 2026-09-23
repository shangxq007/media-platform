package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot;

/** Persistence boundary for snapshots admitted to durable workflows. */
public interface FeatureFlagSnapshotStore {
    void persist(FeatureFlagSnapshot snapshot);
    FeatureFlagSnapshot load(String snapshotId);
}
