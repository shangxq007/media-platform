package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.sandbox.execution.TaskCapability;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for the media.cover-image capability: vocabulary, SPI/registry fail-closed
 * behaviour and provider manifest. No fake provider result is asserted anywhere.
 */
class CoverImageCapabilityTest {

    private static CoverImageContracts.Request request(String format) {
        return new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 1.5d, format, 640, 80, "cover-key-1");
    }

    /** Minimal provider double used only to exercise registry composition rules. */
    private static final class Provider implements CoverImageCapabilityProvider {
        private final Manifest manifest;

        Provider(String providerId, String capabilityId) {
            this.manifest = new Manifest(capabilityId, providerId, "1.0.0", "ffmpeg",
                    Set.of("video/mp4"), Set.of("png"), 0d, 86_400d, 16, 8192, 1024L, 60,
                    "sandbox-bwrap", "ffmpeg");
        }

        @Override public Manifest manifest() { return manifest; }

        @Override public Result render(CoverImageContracts.Request request, Path inputPath,
                                       BooleanSupplier cancelled) {
            throw new AssertionError("provider execution is not part of this contract test");
        }
    }

    @Test
    void vocabularyExtensionsExistWithoutArtifactModelChanges() {
        assertThat(ProvenanceRelationType.valueOf("COVER_OF")).isNotNull();
        assertThat(TaskCapability.valueOf("COVER_IMAGE")).isNotNull();
        assertThat(CoverImageContracts.CAPABILITY).isEqualTo("media.cover-image");
        assertThat(CoverImageContracts.PROVIDER).isEqualTo("platform-ffmpeg-cover-image");
    }

    @Test
    void registryFailsClosedWhenThePinnedProviderIsAbsent() {
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new Provider("some-other-provider", CoverImageContracts.CAPABILITY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned media.cover-image provider is not registered");
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new Provider("unrelated", "media.unrelated"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registryRejectsDuplicateProviderIdsAndIgnoresForeignCapabilities() {
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(List.of(
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY),
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate cover-image provider");

        var registry = new CoverImageCapabilityRegistry(List.of(
                new Provider("unrelated", "media.unrelated"),
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY)));
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.provider().manifest().capabilityId())
                .isEqualTo(CoverImageContracts.CAPABILITY);
        assertThat(registry.provider().manifest().outputFormats()).containsExactly("png");
        assertThatThrownBy(() -> registry.provider("unknown").manifest())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unregistered cover-image provider");
    }

    @Test
    void requestContractRejectsInvalidInputs() {
        assertThat(request("png").subjectArtifactId()).isEqualTo("art_subject");
        assertThatThrownBy(() -> request("gif")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", -1d, "png", null, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 0d, "png", 8, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 0d, "png", null, 0, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", " ", 0d, "png", null, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
