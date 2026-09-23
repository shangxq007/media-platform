package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot;

/** Persistence boundary for snapshots admitted to durable workflows. */
public interface FeatureFlagSnapshotStore {
    void persist(FeatureFlagSnapshot snapshot);
    FeatureFlagSnapshot load(String snapshotId);

    default FeatureFlagSnapshot loadForScope(String snapshotId, String tenantId, String workspaceId) {
        FeatureFlagSnapshot snapshot = load(snapshotId);
        if (snapshot == null || tenantId == null || !tenantId.equals(snapshot.tenantId())
                || !java.util.Objects.equals(workspaceId, snapshot.workspaceId())) {
            throw new IllegalArgumentException("feature flag snapshot is outside the authenticated scope");
        }
        return snapshot;
    }
}
