package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ThumbnailArchitectureGuardTest {
    @Test
    void temporalActivityDoesNotOwnProcessExecutionOrArtifactAuthority() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/platform/thumbnail/ThumbnailActivitiesImpl.java"));
        assertThat(source).doesNotContain("ProcessBuilder", "Runtime.getRuntime", "ffmpeg", "ffprobe");
        assertThat(source).contains("ThumbnailProviderInvoker", "ThumbnailCommitService");
        // The retired slice-local registry must not reappear in the activity path.
        assertThat(source).doesNotContain("ThumbnailCapabilityRegistry");
    }

    /**
     * COVER-THUMBNAIL-REBUILD-001 (action 2): the slice-local registry is retired and replaced by
     * the worker execution adapter; capability discovery/registration is the platform capability
     * registry's job (ThumbnailPlatformRegistration).
     */
    @Test
    void sliceLocalCapabilityRegistryIsRetiredInFavourOfTheWorkerAdapter() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailCapabilityRegistry.java")))
                .as("the slice-local registry is deleted")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailProviderInvoker.java")))
                .as("the worker execution adapter exists")
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailPlatformRegistration.java")))
                .as("the unified platform registration exists")
                .isTrue();
    }

    @Test
    void providerManifestPinsCapabilityToolchainLimitsAndTrust() {
        var manifest = new CpuFrameExtractThumbnailProvider("./.data/storage", "ffmpeg", "ffprobe").manifest();
        // The manifest declares a capability list (N:M-capable), aligned with the cover manifest;
        // this slice's provider declares exactly media.thumbnail.
        assertThat(manifest.capabilities())
                .extracting(ThumbnailCapabilityProvider.CapabilityDeclaration::capabilityId)
                .containsExactly("media.thumbnail");
        assertThat(manifest.capabilities().getFirst().contractVersion()).isEqualTo("1.0");
        assertThat(manifest.supports("media.thumbnail")).isTrue();
        assertThat(manifest.supports("media.cover-image")).isFalse();
        // Provider identity is a capability-independent family plus a separate implementation slot,
        // aligned with the cover-image manifest; the implementation id must never sit in the
        // provider-id position.
        assertThat(manifest.providerId()).isEqualTo("platform.ffmpeg");
        assertThat(manifest.providerImplementationId()).isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(manifest.providerId()).isNotEqualTo(manifest.providerImplementationId());
        assertThat(CpuFrameExtractThumbnailProvider.PROVIDER_ID).isEqualTo("platform.ffmpeg");
        assertThat(CpuFrameExtractThumbnailProvider.PROVIDER_IMPLEMENTATION_ID)
                .isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(manifest.providerVersion()).isEqualTo("1.0.0");
        assertThat(manifest.inputFormats()).contains("video/*");
        assertThat(manifest.outputFormats()).containsExactlyInAnyOrder("image/jpeg", "image/png");
        assertThat(manifest.maximumInputBytes()).isEqualTo(512L * 1024L * 1024L);
        assertThat(manifest.trustRequirement()).isEqualTo("trusted-provider");
        assertThat(manifest.runtimeRequirement()).isEqualTo("worker-runtime.local-process");
    }
}
