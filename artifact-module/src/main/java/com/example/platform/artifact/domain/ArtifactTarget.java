package com.example.platform.artifact.domain;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/**
 * Exact artifact-facing target pin (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, Q7/E-Q1).
 *
 * <p>A new artifact-facing contract carrying the immutable exact pin of one
 * Artifact: business identity ({@link ArtifactId}) plus content digest
 * ({@link ContentDigest}). It mirrors the existing exact-pin shape
 * ({@code ArtifactPinService.ArtifactPin(ArtifactId, ContentDigest)}) without
 * resurrecting the retired shared-kernel {@code ArtifactRef} and without
 * depending on a {@code platform-app} service. Tenant/workspace scope is
 * supplied by the invocation context and must never become a second target
 * authority.</p>
 */
public record ArtifactTarget(ArtifactId artifactId, ContentDigest contentDigest) {

    public ArtifactTarget {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(contentDigest, "contentDigest");
    }
}
