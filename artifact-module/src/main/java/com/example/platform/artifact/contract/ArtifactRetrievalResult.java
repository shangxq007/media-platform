package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;

import java.util.Objects;

/** Complete, verified read facts returned by the canonical retrieval port. */
public record ArtifactRetrievalResult(
        ArtifactId artifactId,
        ArtifactScope scope,
        ArtifactStorageReference storage,
        ContentDigest verifiedDigest,
        long verifiedSizeBytes,
        ArtifactState lifecycle,
        String lineageFingerprint
) {
    public ArtifactRetrievalResult {
        Objects.requireNonNull(artifactId, "artifactId"); Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(storage, "storage"); Objects.requireNonNull(verifiedDigest, "verifiedDigest"); Objects.requireNonNull(lifecycle, "lifecycle");
        if (verifiedSizeBytes < 0) throw new ArtifactContractException(ArtifactContractErrorCode.INTEGRITY_VERIFICATION_FAILED, "verified size is invalid");
        if (lineageFingerprint == null || lineageFingerprint.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "lineageFingerprint is required");
    }
}
