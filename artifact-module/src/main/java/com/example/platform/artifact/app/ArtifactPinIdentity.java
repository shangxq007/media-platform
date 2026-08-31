package com.example.platform.artifact.app;

import com.example.platform.shared.digest.CanonicalCommandFingerprint;

/** Deterministic, versioned identity for the immutable Artifact-pin ownership tuple. */
public final class ArtifactPinIdentity {

    public static final int MAX_LENGTH = 64;
    private static final String VERSION_PREFIX = "p1";

    private ArtifactPinIdentity() {
    }

    public static String forRevisionArtifact(
            String tenantId, String projectId, String revisionId, String artifactId) {
        String hash = CanonicalCommandFingerprint.builder("ARTIFACT_PIN_IDENTITY")
                .required("tenantId", tenantId)
                .required("projectId", projectId)
                .required("revisionId", revisionId)
                .required("artifactId", artifactId)
                .sha256Hex();
        // 248 bits of the typed SHA-256 commitment plus the explicit identity version.
        return VERSION_PREFIX + hash.substring(0, MAX_LENGTH - VERSION_PREFIX.length());
    }
}
