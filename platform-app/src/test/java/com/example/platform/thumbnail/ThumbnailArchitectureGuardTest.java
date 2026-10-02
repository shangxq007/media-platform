package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.coverimage.CoverImageContracts;
import com.example.platform.frameextract.FfmpegCpuProvider;
import com.example.platform.frameextract.FrameExtractCapabilityDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ThumbnailArchitectureGuardTest {
    @Test
    void temporalActivityDoesNotOwnProcessExecutionOrArtifactAuthority() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/platform/thumbnail/ThumbnailActivitiesImpl.java"));
        assertThat(source).doesNotContain("ProcessBuilder", "Runtime.getRuntime", "ffmpeg", "ffprobe");
        assertThat(source).contains("FrameExtractExecutionAdapter", "ThumbnailCommitService");
        // The retired slice-local registry must not reappear in the activity path.
        assertThat(source).doesNotContain("ThumbnailCapabilityRegistry");
    }

    /**
     * COVER-THUMBNAIL-UNIFY-001: the slice-local registries and the per-capability worker invokers are
     * retired in favour of one capability-neutral worker execution adapter and one capability-neutral
     * platform registration; capability discovery/registration is the platform capability registry's
     * job (FrameExtractPlatformRegistration).
     */
    @Test
    void sliceLocalRegistriesAreRetiredInFavourOfTheUnifiedAdapter() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailCapabilityRegistry.java")))
                .as("the retired thumbnail slice-local registry is gone")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailProviderInvoker.java")))
                .as("the per-capability thumbnail worker invoker is retired")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/coverimage/CoverImageCapabilityRegistry.java")))
                .as("the retired cover slice-local registry is gone")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/coverimage/CpuFrameExtractCoverImageProvider.java")))
                .as("the capability-scoped cover provider is retired")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/CpuFrameExtractThumbnailProvider.java")))
                .as("the capability-scoped thumbnail provider is retired")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/frameextract/FrameExtractExecutionAdapter.java")))
                .as("the single capability-neutral worker execution adapter exists")
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/frameextract/FfmpegCpuProvider.java")))
                .as("the single capability-neutral provider exists")
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/frameextract/FrameExtractPlatformRegistration.java")))
                .as("the single capability-neutral platform registration exists")
                .isTrue();
    }

    @Test
    void providerManifestPinsCapabilityToolchainLimitsAndTrust() {
        var manifest = new FfmpegCpuProvider("ffmpeg", "ffprobe").manifest();
        // One capability-neutral provider declares the capability list (N:M production topology):
        // both media.cover-image and media.thumbnail are served by the one implementation.
        assertThat(manifest.capabilities())
                .extracting(FrameExtractCapabilityDeclaration::capabilityId)
                .containsExactlyInAnyOrder(CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        assertThat(manifest.capabilities())
                .extracting(FrameExtractCapabilityDeclaration::contractVersion)
                .containsOnly("1.0");
        assertThat(manifest.supports("media.thumbnail")).isTrue();
        assertThat(manifest.supports("media.cover-image")).isTrue();
        // Provider identity is a capability-independent family plus a separate implementation slot;
        // the implementation id must never sit in the provider-id position.
        assertThat(manifest.providerId()).isEqualTo("platform.ffmpeg");
        assertThat(manifest.providerImplementationId()).isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(manifest.providerId()).isNotEqualTo(manifest.providerImplementationId());
        assertThat(FfmpegCpuProvider.PROVIDER_ID).isEqualTo("platform.ffmpeg");
        assertThat(FfmpegCpuProvider.PROVIDER_IMPLEMENTATION_ID)
                .isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(manifest.providerVersion()).isEqualTo("1.0.0");
        assertThat(manifest.inputFormats()).contains("video/*");
        assertThat(manifest.outputFormats()).containsExactlyInAnyOrder("image/jpeg", "image/png");
        assertThat(manifest.maximumInputBytes()).isEqualTo(512L * 1024L * 1024L);
        // The capability-scoped trust/runtime requirements live in the per-capability profiles: the
        // thumbnail profile keeps its trusted-provider / worker-runtime shape.
        var thumbnailProfile = FfmpegCpuProvider.profiles().get(ThumbnailContracts.CAPABILITY);
        assertThat(thumbnailProfile.trustRequirement()).isEqualTo("trusted-provider");
        assertThat(thumbnailProfile.runtimeRequirement()).isEqualTo("worker-runtime.local-process");
        assertThat(thumbnailProfile.maximumWidth()).isEqualTo(4096);
    }
}
