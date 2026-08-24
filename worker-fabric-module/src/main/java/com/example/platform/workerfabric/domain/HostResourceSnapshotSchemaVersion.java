package com.example.platform.workerfabric.domain;

/** Version of the runtime-only host resource evidence schema. */
public record HostResourceSnapshotSchemaVersion(int value) {

    public static final HostResourceSnapshotSchemaVersion CURRENT =
            new HostResourceSnapshotSchemaVersion(1);

    public HostResourceSnapshotSchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("host resource snapshot schema version must be positive");
        }
    }
}
