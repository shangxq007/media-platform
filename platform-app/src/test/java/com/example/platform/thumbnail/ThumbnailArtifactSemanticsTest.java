package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * COVER-THUMBNAIL-REBUILD-001 (action 5): the thumbnail artifact semantics are aligned with the
 * cover relation model — a thumbnail is an image Artifact whose thumbnail role is the
 * {@code THUMBNAIL_OF} provenance relation, not a dedicated {@code ArtifactKind}.
 */
class ThumbnailArtifactSemanticsTest {

    @Test
    void thumbnailRelationExistsAndTheLegacyArtifactKindIsRetained() {
        assertThat(ProvenanceRelationType.valueOf("THUMBNAIL_OF")).isNotNull();
        // Retained for rows committed before the rebuild; not removed (no legacy data loss).
        assertThat(ArtifactKind.valueOf("THUMBNAIL")).isNotNull();
    }

    @Test
    void commitPathUsesTheRelationModelInsteadOfTheDedicatedKind() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailCommitService.java"));
        assertThat(source).contains("ArtifactKind.DERIVED_MEDIA")
                .contains("ProvenanceRelationType.THUMBNAIL_OF")
                .contains("ThumbnailContracts.OPERATION_ID")
                .doesNotContain("ArtifactKind.THUMBNAIL");
    }

    @Test
    void readServiceAcceptsTheRelationModelAndRetainsLegacyRows() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailArtifactReadService.java"));
        assertThat(source).contains("ProvenanceRelationType.THUMBNAIL_OF")
                .contains("ArtifactKind.DERIVED_MEDIA")
                .contains("ArtifactKind.THUMBNAIL");
    }
}
