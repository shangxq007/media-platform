package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test/local-only snapshot store. Production wiring must use platform PostgreSQL. */
public final class InMemoryFeatureFlagSnapshotStore implements FeatureFlagSnapshotStore {
    private final Map<String, FeatureFlagSnapshot> snapshots = new ConcurrentHashMap<>();
    @Override public void persist(FeatureFlagSnapshot snapshot) { snapshots.put(snapshot.snapshotId(), snapshot); }
    @Override public FeatureFlagSnapshot load(String snapshotId) {
        FeatureFlagSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) throw new IllegalArgumentException("Unknown feature snapshot: " + snapshotId);
        return snapshot;
    }
}
