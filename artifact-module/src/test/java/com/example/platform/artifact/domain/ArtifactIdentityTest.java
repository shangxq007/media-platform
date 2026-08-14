package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Artifact identity, immutability, and content-digest separation.
 */
@DisplayName("Artifact Identity and Immutability")
class ArtifactIdentityTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");
    private static final ContentDigest DIGEST_1 = ContentDigest.sha256("a".repeat(64));
    private static final ContentDigest DIGEST_2 = ContentDigest.sha256("b".repeat(64));

    @Test
    @DisplayName("Artifact identity is independent of content — same bytes, different source = different ArtifactId")
    void identityIndependentOfContent() {
        ArtifactId id1 = new ArtifactId("art-001");
        ArtifactId id2 = new ArtifactId("art-002");

        Artifact a1 = new Artifact(id1, "tenant-1", DIGEST_1, 1000L, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        Artifact a2 = new Artifact(id2, "tenant-1", DIGEST_1, 1000L, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(a1.artifactId()).isNotEqualTo(a2.artifactId());
        assertThat(a1.contentDigest()).isEqualTo(a2.contentDigest());
    }

    @Test
    @DisplayName("New content (even single byte change) yields new Artifact with new id")
    void newContentYieldsNewArtifact() {
        ArtifactId id1 = new ArtifactId("art-001");
        ArtifactId id2 = new ArtifactId("art-002");

        Artifact a1 = new Artifact(id1, "tenant-1", DIGEST_1, 1000L, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        Artifact a2 = new Artifact(id2, "tenant-1", DIGEST_2, 1001L, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    @DisplayName("Artifact is immutable — withState returns new instance")
    void artifactIsImmutable() {
        Artifact original = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.REGISTERING, 1, NOW);

        Artifact transitioned = original.withState(ArtifactState.AVAILABLE);

        assertThat(original.state()).isEqualTo(ArtifactState.REGISTERING);
        assertThat(transitioned.state()).isEqualTo(ArtifactState.AVAILABLE);
        assertThat(transitioned.artifactId()).isEqualTo(original.artifactId());
        assertThat(transitioned.contentDigest()).isEqualTo(original.contentDigest());
        assertThat(transitioned.byteLength()).isEqualTo(original.byteLength());
    }

    @Test
    @DisplayName("Immutable fields cannot change via withState")
    void immutableFieldsPreserved() {
        Artifact original = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.REGISTERING, 1, NOW);

        Artifact transitioned = original.withState(ArtifactState.AVAILABLE);

        assertThat(transitioned.artifactId()).isEqualTo(original.artifactId());
        assertThat(transitioned.tenantId()).isEqualTo(original.tenantId());
        assertThat(transitioned.contentDigest()).isEqualTo(original.contentDigest());
        assertThat(transitioned.byteLength()).isEqualTo(original.byteLength());
        assertThat(transitioned.mediaType()).isEqualTo(original.mediaType());
        assertThat(transitioned.artifactKind()).isEqualTo(original.artifactKind());
        assertThat(transitioned.schemaVersion()).isEqualTo(original.schemaVersion());
        assertThat(transitioned.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    @DisplayName("Artifact validates null fields")
    void validatesNullFields() {
        assertThatThrownBy(() -> new Artifact(null, "tenant-1", DIGEST_1, 1000L, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Artifact validates negative byteLength")
    void validatesNegativeByteLength() {
        assertThatThrownBy(() -> new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, -1L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Artifact validates schemaVersion >= 1")
    void validatesSchemaVersion() {
        assertThatThrownBy(() -> new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Canonical serialization is deterministic")
    void canonicalSerializationDeterministic() {
        Artifact a = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(a.canonicalForm()).isEqualTo(a.canonicalForm());
    }

    @Test
    @DisplayName("Same semantic Artifact produces same canonical form")
    void sameSemanticArtifactSameCanonicalForm() {
        Artifact a1 = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        Artifact a2 = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(a1.canonicalForm()).isEqualTo(a2.canonicalForm());
    }

    @Test
    @DisplayName("Different semantic Artifact produces different canonical form")
    void differentSemanticArtifactDifferentCanonicalForm() {
        Artifact a1 = new Artifact(new ArtifactId("art-001"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);
        Artifact a2 = new Artifact(new ArtifactId("art-002"), "tenant-1", DIGEST_1, 1000L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, NOW);

        assertThat(a1.canonicalForm()).isNotEqualTo(a2.canonicalForm());
    }
}
