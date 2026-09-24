package com.example.platform.artifact.contract;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/** Immutable timeline/render input reference owned by the platform, independent of MediaAsset. */
public record ArtifactSourceReference(
        int contractVersion,
        ArtifactId artifactId,
        ContentDigest sourceDigest,
        ArtifactScope scope,
        String lineageFingerprint,
        long artifactRevision
) {
    public static final int CURRENT_CONTRACT_VERSION = 1;
    public ArtifactSourceReference {
        if (contractVersion != CURRENT_CONTRACT_VERSION) throw new ArtifactContractException(ArtifactContractErrorCode.CONFLICTING_FACT, "unsupported source contract version");
        Objects.requireNonNull(artifactId, "artifactId"); Objects.requireNonNull(sourceDigest, "sourceDigest"); Objects.requireNonNull(scope, "scope");
        if (lineageFingerprint == null || lineageFingerprint.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "lineageFingerprint is required");
        if (artifactRevision < 1) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "artifactRevision is required");
    }
}
