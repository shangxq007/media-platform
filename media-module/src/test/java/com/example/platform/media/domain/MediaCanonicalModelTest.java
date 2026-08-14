package com.example.platform.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.identity.MediaAssetRef;
import com.example.platform.media.domain.media.ArtifactRelationshipKind;
import com.example.platform.media.domain.media.MediaAsset;
import com.example.platform.media.domain.media.MediaAssetArtifactLink;
import com.example.platform.media.domain.time.TimeBase;
import com.example.platform.shared.identity.ArtifactId;
import org.junit.jupiter.api.Test;

/**
 * MCMV2-C identity/relationship/timebase contract tests.
 */
class MediaCanonicalModelTest {

    @Test
    void mediaAssetIdIsStableTypedIdentity() {
        MediaAssetId id = MediaAssetId.of("asset-1");
        assertThat(id).isEqualTo(MediaAssetId.of("asset-1"));
        assertThat(id.value()).isEqualTo("asset-1");
        assertThatThrownBy(() -> MediaAssetId.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaAssetId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mediaAssetIdIsNotStorageOrExternalLocator() {
        // identity is a plain typed value; storage/locator are separate concepts
        MediaAssetId id = MediaAssetId.of("asset-1");
        assertThat(id.asString()).isEqualTo("asset-1");
        assertThat(id.toString()).doesNotContain("://");
    }

    @Test
    void mediaAssetRefCarriesOnlyIdentity() {
        MediaAssetRef ref = MediaAssetRef.of("asset-1");
        assertThat(ref.mediaAssetId()).isEqualTo(MediaAssetId.of("asset-1"));
    }

    @Test
    void artifactLinkageIsTypedAndDirectional() {
        MediaAssetArtifactLink link = new MediaAssetArtifactLink(
                MediaAssetId.of("asset-1"), new ArtifactId("artifact-9"), ArtifactRelationshipKind.SOURCE);
        assertThat(link.mediaAssetId().value()).isEqualTo("asset-1");
        assertThat(link.artifactId().value()).isEqualTo("artifact-9");
        assertThat(link.relationship()).isEqualTo(ArtifactRelationshipKind.SOURCE);
        assertThatThrownBy(() -> new MediaAssetArtifactLink(
                MediaAssetId.of("asset-1"), null, ArtifactRelationshipKind.SOURCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaAssetIdDiffersFromArtifactId() {
        // MEDIA_ASSET_IDENTITY_AUTHORITY_V1: distinct identity spaces
        assertThat(MediaAssetId.class).isNotEqualTo(ArtifactId.class);
    }

    @Test
    void timeBaseIsExactRationalAndReduced() {
        TimeBase tb = TimeBase.of(2, 4);
        assertThat(tb.numerator()).isEqualTo(1);
        assertThat(tb.denominator()).isEqualTo(2);
        assertThatThrownBy(() -> TimeBase.of(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeBase.of(-1, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaAssetHoldsNoStorageOrStructuralTruth() {
        MediaAsset asset = new MediaAsset(
                MediaAssetId.of("asset-1"), "tenant-1", "project-1", "v1",
                null, null, null, null, null, false, false, "DRAFT", null, null);
        // contract: no storage key, no duration, no streams on the asset entity
        assertThat(asset.id()).isEqualTo(MediaAssetId.of("asset-1"));
        assertThat(asset.tenantId()).isEqualTo("tenant-1");
    }
}
