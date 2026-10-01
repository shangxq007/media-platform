package com.example.platform.frameextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.coverimage.CoverImageContracts;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.thumbnail.ThumbnailContracts;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for the merged, capability-neutral frame-extract provider: one provider
 * family plus one implementation declares a capability <em>list</em>, the manifest vocabulary is
 * capability-independent, and everything that genuinely differs per capability is carried by a
 * profile selected from the executing {@code capabilityId}.
 */
class FrameExtractProviderContractTest {

    @Test
    void providerIdentityIsCapabilityIndependent() {
        assertThat(ProvenanceRelationType.valueOf("COVER_OF")).isNotNull();
        assertThat(ProvenanceRelationType.valueOf("THUMBNAIL_OF")).isNotNull();
        assertThat(TaskCapability.valueOf("COVER_IMAGE")).isNotNull();
        assertThat(TaskCapability.valueOf("THUMBNAIL")).isNotNull();

        assertThat(FfmpegCpuFrameExtractProvider.PROVIDER_ID).isEqualTo("platform.ffmpeg");
        assertThat(FfmpegCpuFrameExtractProvider.PROVIDER_IMPLEMENTATION_ID)
                .isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(FfmpegCpuFrameExtractProvider.PROVIDER_ID)
                .doesNotContain(CoverImageContracts.CAPABILITY)
                .doesNotContain(ThumbnailContracts.CAPABILITY);
        assertThat(FfmpegCpuFrameExtractProvider.PROVIDER_IMPLEMENTATION_ID)
                .doesNotContain(CoverImageContracts.CAPABILITY)
                .doesNotContain(ThumbnailContracts.CAPABILITY);
    }

    @Test
    void oneProviderDeclaresBothCapabilitiesAsAList() {
        FrameExtractManifest manifest =
                new FfmpegCpuFrameExtractProvider("ffmpeg", "ffprobe").manifest();

        assertThat(manifest.capabilities())
                .extracting(FrameExtractCapabilityDeclaration::capabilityId)
                .containsExactlyInAnyOrder(
                        CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        assertThat(manifest.capabilities())
                .extracting(FrameExtractCapabilityDeclaration::contractVersion)
                .containsOnly("1.0");
        assertThat(manifest.supports(CoverImageContracts.CAPABILITY)).isTrue();
        assertThat(manifest.supports(ThumbnailContracts.CAPABILITY)).isTrue();
        assertThat(manifest.supports("media.unrelated")).isFalse();
        assertThat(manifest.providerId()).isNotEqualTo(manifest.providerImplementationId());
    }

    @Test
    void capabilityProfilesCarryThePerCapabilityDifferences() {
        var profiles = FfmpegCpuFrameExtractProvider.profiles();
        assertThat(profiles).containsOnlyKeys(
                CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);

        var cover = profiles.get(CoverImageContracts.CAPABILITY);
        assertThat(cover.capabilityId()).isEqualTo("media.cover-image");
        assertThat(cover.contractVersion()).isEqualTo("1.0");
        assertThat(cover.maximumWidth()).isEqualTo(8192);
        assertThat(cover.timeoutSeconds()).isEqualTo(120);
        assertThat(cover.inputFormats()).contains("video/mp4");
        assertThat(cover.outputFormats()).containsExactlyInAnyOrder("image/png", "image/jpeg");

        var thumbnail = profiles.get(ThumbnailContracts.CAPABILITY);
        assertThat(thumbnail.capabilityId()).isEqualTo("media.thumbnail");
        assertThat(thumbnail.contractVersion()).isEqualTo("1.0");
        assertThat(thumbnail.maximumWidth()).isEqualTo(4096);
        assertThat(thumbnail.timeoutSeconds()).isEqualTo(60);
        assertThat(thumbnail.inputFormats()).contains("video/*");
    }

    @Test
    void manifestRejectsEmptyAndDuplicateCapabilityDeclarations() {
        FrameExtractCapabilityDeclaration cover = new FrameExtractCapabilityDeclaration(
                CoverImageContracts.CAPABILITY, CoverImageContracts.CAPABILITY_VERSION);

        assertThatThrownBy(() -> new FrameExtractManifest(
                "platform.ffmpeg", "impl", "1.0.0", List.of(), "ffmpeg",
                Set.of("video/*"), Set.of("image/png"), 0d, 86_400d, 16, 8192, 1024L, 60, "t", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one capability");
        assertThatThrownBy(() -> new FrameExtractManifest(
                "platform.ffmpeg", "impl", "1.0.0", List.of(cover, cover), "ffmpeg",
                Set.of("video/*"), Set.of("image/png"), 0d, 86_400d, 16, 8192, 1024L, 60, "t", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate capability declaration");
        assertThatThrownBy(() -> new FrameExtractCapabilityDeclaration(" ", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerFailsClosedForAnUndeclaredCapabilityAndAMissingInput() {
        var provider = new FfmpegCpuFrameExtractProvider("ffmpeg", "ffprobe");

        assertThat(provider.render("media.unrelated", Path.of("/tmp/in"), Path.of("/tmp/work"),
                "png", null, null, 0d, () -> false).failureCode())
                .isEqualTo("UNSUPPORTED_CAPABILITY");
        assertThat(provider.render(CoverImageContracts.CAPABILITY, Path.of("/tmp/absent-input"),
                Path.of("/tmp/work"), "png", null, null, 0d, () -> false).failureCode())
                .isEqualTo("INPUT_UNAVAILABLE");
    }

    /** A routing-only double; it never fabricates a rendered frame. */
    static final class RoutingProvider implements FrameExtractProvider {

        private final FrameExtractManifest manifest;

        RoutingProvider(String providerId, String implementationId, String... capabilityIds) {
            List<FrameExtractCapabilityDeclaration> declarations = java.util.Arrays.stream(capabilityIds)
                    .map(capabilityId -> new FrameExtractCapabilityDeclaration(capabilityId, "1.0"))
                    .toList();
            this.manifest = new FrameExtractManifest(
                    providerId, implementationId, "1.0.0", declarations, "ffmpeg",
                    Set.of("video/*"), Set.of("image/png"), 0d, 86_400d, 16, 8192, 1024L, 60, "t", "r");
        }

        @Override
        public FrameExtractManifest manifest() {
            return manifest;
        }

        @Override
        public FrameExtractResult render(String capabilityId, Path input, Path workDirectory,
                String imageFormat, Integer width, Integer quality, double timestampSeconds,
                BooleanSupplier cancelled) {
            if (!manifest.supports(capabilityId)) {
                return FrameExtractResult.failure("UNSUPPORTED_CAPABILITY");
            }
            return FrameExtractResult.failure("CONTRACT-TEST-ROUTING-ONLY");
        }
    }
}
