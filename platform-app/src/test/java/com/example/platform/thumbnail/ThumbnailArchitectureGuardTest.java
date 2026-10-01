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
        assertThat(source).contains("ThumbnailCapabilityRegistry", "ThumbnailCommitService");
    }

    @Test
    void providerManifestPinsCapabilityToolchainLimitsAndTrust() {
        var manifest = new CpuFrameExtractThumbnailProvider("./.data/storage", "ffmpeg", "ffprobe").manifest();
        assertThat(manifest.capabilityId()).isEqualTo("media.thumbnail");
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
