package com.example.platform.artifact.domain;

import java.util.Objects;

/** All immutable commit intent, excluding a retry's evaluation/creation clock readings. */
public final class ArtifactCommitIdentity {
    private ArtifactCommitIdentity() {}
    public static boolean sameIntent(ArtifactCommitRequest a, ArtifactCommitRequest b) {
        return a.artifactId().equals(b.artifactId()) && a.tenantId().equals(b.tenantId())
                && a.contentDigest().matches(b.contentDigest()) && a.byteLength()==b.byteLength()
                && a.mediaType()==b.mediaType() && a.artifactKind()==b.artifactKind()
                && a.schemaVersion()==b.schemaVersion() && a.storageObjectId().equals(b.storageObjectId())
                && a.storageReplicaId().equals(b.storageReplicaId()) && a.providerId().equals(b.providerId())
                && a.replicaRole()==b.replicaRole() && a.region().equals(b.region())
                && a.idempotencyKey().equals(b.idempotencyKey())
                && a.provenanceDeclarations().equals(b.provenanceDeclarations())
                && Objects.equals(a.renderJobId(),b.renderJobId()) && Objects.equals(a.projectId(),b.projectId());
    }
}
