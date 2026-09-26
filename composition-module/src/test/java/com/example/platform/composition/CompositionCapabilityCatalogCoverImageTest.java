package com.example.platform.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.composition.app.RegistryAvailabilityProjection;
import com.example.platform.composition.domain.CompositionModels.Availability;
import com.example.platform.composition.domain.CompositionModels.ExecutionMode;
import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistries;
import org.junit.jupiter.api.Test;

/**
 * COVER-PROVIDER-PLATFORM-REGISTER-001: {@code media.cover-image} is part of the composition
 * capability catalog, declared on the platform Artifact contract (subject Artifact in, cover
 * Artifact out). The entry is declared by the platform, not derived from a provider being loaded,
 * so it is listed even when no provider implementation is registered — and it must never be
 * reported AVAILABLE on the strength of an unregistered slice-local runtime.
 */
class CompositionCapabilityCatalogCoverImageTest {

    private static RegistryAvailabilityProjection catalog() {
        var registry = PluginRegistries.standalone();
        return new RegistryAvailabilityProjection((CapabilityRegistryPort) registry, registry);
    }

    @Test
    void catalogDeclaresMediaCoverImageOnThePlatformArtifactContract() {
        var entry = catalog().publicAvailability().stream()
                .filter(capability -> capability.capabilityId().equals("media.cover-image"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "media.cover-image missing from the composition capability catalog"));

        assertThat(entry.version()).isEqualTo("1.0");
        assertThat(entry.input().name()).isEqualTo("Artifact");
        assertThat(entry.output().name()).isEqualTo("Artifact");
        assertThat(entry.executionModes()).contains(ExecutionMode.ASYNCHRONOUS);
        assertThat(entry.assetTypes()).contains("image");
        assertThat(entry.mediaTypes()).contains("video/mp4");
        assertThat(entry.reliability().cancellable()).isTrue();
        assertThat(entry.reliability().retryable()).isTrue();
        // The slice-local runtime is not the platform execution seam: no provider is registered in
        // this catalog composition, so the projection must be UNAVAILABLE (fail-closed, never
        // advertised as composable).
        assertThat(entry.availability()).isEqualTo(Availability.UNAVAILABLE);
    }

    @Test
    void catalogStillContainsThePreExistingCapabilities() {
        var ids = catalog().publicAvailability().stream()
                .map(capability -> capability.capabilityId())
                .toList();
        assertThat(ids).contains("media.transcode", "media.thumbnail", "media.cover-image");
    }

    @Test
    void catalogResolvesCoverImageByCapabilityAndVersionRange() {
        var projection = catalog();
        assertThat(projection.resolve("media.cover-image", "1.0")).isPresent();
        assertThat(projection.resolve("media.cover-image", ">=1.0")).isPresent();
        assertThat(projection.resolve("media.cover-image", "2.0")).isEmpty();
    }
}
