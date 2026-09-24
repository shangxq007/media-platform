package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/** Publicly safe Marketplace subject; storage/provider details never cross this boundary. */
public record ArtifactSubject(
        int contractVersion,
        ArtifactId artifactId,
        ArtifactScope scope,
        Visibility visibility,
        String ownerId,
        String lineageFingerprint,
        String lifecycleFingerprint
) {
    public static final int CURRENT_CONTRACT_VERSION = 1;
    public ArtifactSubject {
        if (contractVersion != CURRENT_CONTRACT_VERSION) throw new ArtifactContractException(ArtifactContractErrorCode.CONFLICTING_FACT, "unsupported subject contract version");
        Objects.requireNonNull(artifactId, "artifactId"); Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(visibility, "visibility");
        requireText(ownerId, "ownerId"); requireText(lineageFingerprint, "lineageFingerprint"); requireText(lifecycleFingerprint, "lifecycleFingerprint");
    }
    private static void requireText(String v, String n) { if (v == null || v.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, n + " is required"); }
    public enum Visibility { PRIVATE, WORKSPACE, TENANT, PUBLIC }
}
