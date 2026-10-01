package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.capability.FrameWidth;
import com.example.platform.shared.capability.JpegQuality;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.capability.RasterImageEncoding;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.thumbnail.ThumbnailContracts;
import com.example.platform.thumbnail.ThumbnailController;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Proves the cover and thumbnail slices share one parameter authority
 * ({@code shared.capability.MediaFrameExtractParametersV1}) and that the migration did not change
 * the HTTP wire contract (non-breaking).
 */
class CoverThumbnailParameterParityTest {

    @Test
    void identicalTransportParametersProduceTheSameSharedCapabilityParameter() {
        var cover = new CoverImageContracts.Request(
                "tenant", "project", "art_subject", 1.25d, "jpeg", 640, 80, "key");
        var thumbnail = new ThumbnailContracts.Request(
                "tenant", "project", "art_subject", 1.25d, "jpeg", 640, 80, "key");

        MediaFrameExtractParametersV1 coverParameters = cover.capabilityParameters();
        MediaFrameExtractParametersV1 thumbnailParameters = thumbnail.capabilityParameters();

        assertThat(coverParameters).isEqualTo(thumbnailParameters);
        assertThat(coverParameters.position()).isEqualTo(MediaTime.ofRational(5, 4));
        assertThat(coverParameters.encoding())
                .isEqualTo(new RasterImageEncoding.Jpeg(new JpegQuality(80)));
        assertThat(coverParameters.width()).isEqualTo(new FrameWidth.ExplicitPixels(640));
    }

    @Test
    void pngWithNativeWidthAgreesAcrossSlices() {
        var cover = new CoverImageContracts.Request("t", "p", "a", 0d, "png", null, null, "k");
        var thumbnail = new ThumbnailContracts.Request("t", "p", "a", 0d, "png", null, null, "k");

        assertThat(cover.capabilityParameters()).isEqualTo(thumbnail.capabilityParameters());
        assertThat(cover.capabilityParameters().position()).isEqualTo(MediaTime.ZERO);
        assertThat(cover.capabilityParameters().encoding()).isEqualTo(RasterImageEncoding.Png.PNG);
        assertThat(cover.capabilityParameters().width()).isEqualTo(FrameWidth.Native.NATIVE);
    }

    @Test
    void jpegWithoutQualityIsTheDocumentedContractDefault() {
        var cover = new CoverImageContracts.Request("t", "p", "a", 0d, "jpeg", null, null, "k");
        MediaFrameExtractParametersV1 parameters = cover.capabilityParameters();

        assertThat(parameters.encoding()).isEqualTo(RasterImageEncoding.Jpeg.documentedDefault());
        assertThat(((RasterImageEncoding.Jpeg) parameters.encoding()).hasExplicitQuality()).isFalse();
    }

    @Test
    void httpTransportFieldsAreUnchangedByTheAuthorityMigration() {
        assertThat(componentNames(CoverImageController.CreateRequest.class))
                .containsExactly("subjectArtifactId", "timestampSeconds", "imageFormat",
                        "width", "quality", "idempotencyKey");
        assertThat(componentNames(ThumbnailController.Submit.class))
                .containsExactly("sourceAssetId", "timestampSeconds", "imageFormat",
                        "width", "quality", "idempotencyKey");
        assertThat(componentType(CoverImageController.CreateRequest.class, "timestampSeconds"))
                .isEqualTo(double.class);
        assertThat(componentType(ThumbnailController.Submit.class, "timestampSeconds"))
                .isEqualTo(double.class);
    }

    private static java.util.List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static Class<?> componentType(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getName().equals(name))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow();
    }
}
