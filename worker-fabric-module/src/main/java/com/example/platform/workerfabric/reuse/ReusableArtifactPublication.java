package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** Candidate publication produced only after Artifact authority commit. */
public record ReusableArtifactPublication(ReusableArtifactRecord record) {
    public ReusableArtifactPublication {
        Objects.requireNonNull(record, "record");
    }
}
