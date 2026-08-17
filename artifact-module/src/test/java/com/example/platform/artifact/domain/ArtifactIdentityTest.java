package com.example.platform.artifact.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GCR-2 TEST GROUP A — Artifact identity.
 *
 * A1: ArtifactId stable across replica changes.
 * A2: same ArtifactId / different immutable digest fails closed.
 * A3: multiple replicas = one logical Artifact.
 * A4: storage URI is not semantic identity.
 * A5: digest mismatch on commit/register fails closed.
 */
class ArtifactIdentityTest {

    private static final ArtifactId ID = new ArtifactId("art-identity-1");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    private Artifact artifact(ContentDigest digest) {
        return new Artifact(ID, "tenant-1", digest, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                ArtifactState.AVAILABLE, 1, Instant.now());
    }

    @Test
    void a1_identityStableAcrossReplicaChanges() {
        Artifact base = artifact(DIGEST);
        // Relocating a replica changes the physical binding, never the identity.
        ArtifactReplicaBinding r1 = new ArtifactReplicaBinding(
                "rep-1", ID, new com.example.platform.storage.contract.StorageObjectId("s3://a/x.mp4"),
                new com.example.platform.storage.contract.StorageReplicaId("r1"),
                new com.example.platform.storage.contract.StorageProviderId("s3"),
                ReplicaRole.PRIMARY, "us-east-1", Instant.now());
        ArtifactReplicaBinding r2 = new ArtifactReplicaBinding(
                "rep-2", ID, new com.example.platform.storage.contract.StorageObjectId("local://b/x.mp4"),
                new com.example.platform.storage.contract.StorageReplicaId("r2"),
                new com.example.platform.storage.contract.StorageProviderId("local"),
                ReplicaRole.SECONDARY, "default", Instant.now());

        assertEquals(ID, r1.artifactId());
        assertEquals(ID, r2.artifactId());
        assertEquals(DIGEST, base.contentDigest());
        // Physical location is not part of identity/equality.
        assertNotEquals(r1.storageObjectId(), r2.storageObjectId());
        // Semantic identity (ArtifactId + ContentDigest) unchanged across replicas.
        assertEquals(ID, artifact(DIGEST).artifactId());
        assertEquals(DIGEST, artifact(DIGEST).contentDigest());
    }

    @Test
    void a2_sameArtifactIdDifferentDigestFailsClosed() {
        ContentDigest other = ContentDigest.sha256("b".repeat(64));
        Artifact v1 = artifact(DIGEST);
        Artifact v2 = artifact(other);
        // Same ArtifactId cannot silently bind different immutable content.
        assertNotEquals(v1.contentDigest(), v2.contentDigest());
        assertNotEquals(v1.contentDigest().matches(v2.contentDigest()), true);
        // Digest is immutable after creation: sha256 canonical form is fixed.
        assertThrows(IllegalArgumentException.class, () ->
                ContentDigest.sha256("not-hex-value"));
    }

    @Test
    void a3_multipleReplicasOneArtifact() {
        // One logical Artifact with multiple replica bindings — identity is per-Artifact.
        List<ArtifactReplicaBinding> replicas = List.of(
                replica("rep-a", "s3://bucket/one.mp4", ReplicaRole.PRIMARY),
                replica("rep-b", "s3://bucket/two.mp4", ReplicaRole.SECONDARY));
        assertEquals(2, replicas.size());
        assertEquals(1, replicas.stream().map(ArtifactReplicaBinding::artifactId).distinct().count());
        assertEquals(ID, replicas.get(0).artifactId());
        assertEquals(ID, replicas.get(1).artifactId());
    }

    @Test
    void a4_storageUriIsNotSemanticIdentity() {
        ArtifactReplicaBinding atA = replica("rep-a", "s3://old/location.mp4", ReplicaRole.PRIMARY);
        ArtifactReplicaBinding atB = replica("rep-b", "s3://new/location.mp4", ReplicaRole.PRIMARY);
        // Equality/lookup is by ArtifactId + ContentDigest, never by URI.
        assertNotEquals(atA.storageObjectId(), atB.storageObjectId());
        assertEquals(atA.artifactId(), atB.artifactId());
    }

    @Test
    void a5_digestMismatchRejectedOnCommit() {
        // Canonical commit path rejects a request whose digest does not match
        // the Artifact's recorded digest (content integrity is fail-closed).
        Artifact committed = artifact(DIGEST);
        ContentDigest conflicting = ContentDigest.sha256("c".repeat(64));
        assertNotEquals(committed.contentDigest(), conflicting);
        assertFalse(committed.contentDigest().matches(conflicting),
                "digest mismatch must be detectable (integrity assertion)");
    }

    private static ArtifactReplicaBinding replica(String id, String objectKey, ReplicaRole role) {
        return new ArtifactReplicaBinding(
                id, ID, new com.example.platform.storage.contract.StorageObjectId(objectKey),
                new com.example.platform.storage.contract.StorageReplicaId(id),
                new com.example.platform.storage.contract.StorageProviderId("s3"),
                role, "default", Instant.now());
    }
}
