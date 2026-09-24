package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.Objects;

/** Search/index projection. It is derived data and never an Artifact authority. */
public record ArtifactProjection(
        int contractVersion,
        ArtifactId artifactId,
        ArtifactScope scope,
        ArtifactKind kind,
        ContentDigest digest,
        long sizeBytes,
        ArtifactState lifecycle,
        String lineageFingerprint,
        Instant indexedAt,
        long revision
) {
    public static final int CURRENT_CONTRACT_VERSION = 1;
    public ArtifactProjection {
        if (contractVersion != CURRENT_CONTRACT_VERSION) throw new ArtifactContractException(ArtifactContractErrorCode.CONFLICTING_FACT, "unsupported projection contract version");
        Objects.requireNonNull(artifactId, "artifactId"); Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(lifecycle, "lifecycle"); Objects.requireNonNull(indexedAt, "indexedAt");
        if (sizeBytes < 0 || revision < 0) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "projection numeric fact is invalid");
        if (lineageFingerprint == null || lineageFingerprint.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "lineageFingerprint is required");
    }
}
