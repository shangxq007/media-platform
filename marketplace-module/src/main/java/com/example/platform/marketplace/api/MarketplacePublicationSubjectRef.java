package com.example.platform.marketplace.api;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

/**
 * A reference to an owning domain's exact subject, never a new asset identity.
 *
 * <p>MARKETPLACE_SUBJECT_ARTIFACT_IDENTITY_V1 (V28 Path 1b): the canonical subject is an Artifact
 * pin. Artifact is the platform's canonical asset identity; the retired Media identity is no
 * longer expressible here, so this contract cannot be satisfied by a media-keyed subject.
 *
 * <p>The listing/review facts owned by Marketplace (title, description, status, review,
 * workspace/scope admission, aggregate versions) are unchanged by this migration.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(@JsonSubTypes.Type(
        value = MarketplacePublicationSubjectRef.ArtifactSubject.class, name = "ARTIFACT"))
public sealed interface MarketplacePublicationSubjectRef
        permits MarketplacePublicationSubjectRef.ArtifactSubject {

    /**
     * Exact Artifact subject.
     *
     * @param artifactId canonical Artifact identity (Artifact is the sole asset identity authority)
     * @param version    pinned Artifact content digest (canonical SHA-256 hex). Artifact identity is
     *                   immutable — id, tenant and content digest cannot change — so the pin cannot
     *                   silently go stale under the same identity. It replaces the retired
     *                   "exact Media version" equality check.
     */
    record ArtifactSubject(ArtifactId artifactId, String version)
            implements MarketplacePublicationSubjectRef {

        public ArtifactSubject {
            Objects.requireNonNull(artifactId, "artifactId");
            if (version == null) {
                throw new IllegalArgumentException("Exact Artifact content pin required");
            }
            // Canonical SHA-256 validation and normalisation: malformed pins are rejected here,
            // at the contract boundary, instead of being compared loosely downstream.
            version = ContentDigest.sha256(version).canonicalValue();
        }
    }
}
