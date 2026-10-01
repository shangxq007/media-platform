package com.example.platform.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.composition.app.RegistryAvailabilityProjection;
import com.example.platform.composition.domain.CompositionModels.Availability;
import com.example.platform.composition.domain.CompositionModels.ExecutionMode;
import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistries;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COVER-PROVIDER-PLATFORM-REGISTER-001 + COVER-THUMBNAIL-REBUILD-001 (action 1):
 * {@code media.cover-image} and {@code media.thumbnail} are part of the composition capability
 * catalog, declared on the platform Artifact contract (subject Artifact in, produced Artifact out).
 * The entries are declared by the platform, not derived from a provider being loaded, so they are
 * listed even when no provider implementation is registered.
 *
 * <p>Availability is derived from the registered, healthy implementation whose declared contract
 * matches the catalog: with no matching implementation the capability is UNAVAILABLE and does not
 * resolve to a provider binding (fail-closed), and a registered contribution declaring the catalog
 * Artifact contract makes it AVAILABLE and resolvable.
 */
class CompositionCapabilityCatalogCoverImageTest {

    private static RegistryAvailabilityProjection catalog() {
        var registry = PluginRegistries.standalone();
        return new RegistryAvailabilityProjection((CapabilityRegistryPort) registry, registry);
    }

    /** Registry-backed projection plus the registry it was built over (for provider registration). */
    private record Composition(PluginRegistrationPort registry, RegistryAvailabilityProjection projection) {}

    private static Composition composition() {
        PluginRegistrationPort registry = PluginRegistries.standalone();
        return new Composition(registry,
                new RegistryAvailabilityProjection((CapabilityRegistryPort) registry, registry));
    }

    /** A provider contribution declaring one capability with the given reference-type names. */
    private static void registerProvider(
            PluginRegistrationPort registry,
            String pluginId,
            String capabilityId,
            String inputReferenceType,
            String outputReferenceType) {
        registry.registerRuntime(new PluginDescriptor(
                pluginId,
                "1.0.0",
                "1",
                "test-vendor",
                List.of(new CapabilityDescriptor(
                        capabilityId, "1.0", "test", inputReferenceType, outputReferenceType,
                        CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor(
                        "ExecutableTask", "1",
                        "com.example.platform.execution.taskgraph.ExecutableTask",
                        List.of("providerBindingPin"), List.of(),
                        HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("ffmpeg.execute")),
                new ResourceRequirement(
                        1, 256, 50, 0, 64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L, false, 4096, false, 60_000L),
                PluginRuntimeRequirement.trustedInProcess(),
                PluginGuarantee.noneDeclared()));
    }

    private static Availability availabilityOf(RegistryAvailabilityProjection projection, String capabilityId) {
        return projection.publicAvailability().stream()
                .filter(capability -> capability.capabilityId().equals(capabilityId))
                .findFirst().orElseThrow().availability();
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
        // No provider implementation is registered in this catalog composition, so availability is
        // fail-closed UNAVAILABLE.
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

    /**
     * Fail-closed control: a healthy candidate whose declared contract does NOT match the catalog
     * entry keeps the capability UNAVAILABLE, and an UNAVAILABLE capability never resolves to a
     * provider binding.
     */
    @Test
    void unavailableCapabilityDoesNotResolveToAProviderBinding() {
        var underTest = composition();
        registerProvider(underTest.registry(), "media.coverimage.ffmpeg", "media.cover-image",
                "ExecutableTask", "ProviderExecutionOutput");

        assertThat(availabilityOf(underTest.projection(), "media.cover-image"))
                .isEqualTo(Availability.UNAVAILABLE);
        assertThat(underTest.registry().findCapabilityCandidates("media.cover-image", "1.0"))
                .as("a healthy candidate exists, so only the availability gate can make this empty")
                .hasSize(1);
        assertThat(underTest.projection().resolveProviderBound("media.cover-image", "1.0")).isEmpty();
    }

    /**
     * A registered contribution that declares the catalog's Artifact capability contract makes
     * {@code media.cover-image} AVAILABLE and resolvable. This is the corrected catalog semantics
     * for the cover capability (COVER-THUMBNAIL-REBUILD-001, action 1), consistent with the
     * thumbnail control below; availability is derived from a real registration, not pinned.
     */
    @Test
    void registeredContributionDeclaringTheCatalogContractMakesCoverImageAvailable() {
        var underTest = composition();
        registerProvider(underTest.registry(), "media.coverimage.ffmpeg", "media.cover-image",
                "Artifact", "Artifact");

        assertThat(availabilityOf(underTest.projection(), "media.cover-image"))
                .isEqualTo(Availability.AVAILABLE);
        assertThat(underTest.projection().resolveProviderBound("media.cover-image", "1.0"))
                .isPresent()
                .get()
                .extracting(binding -> binding.providerRegistryReference())
                .isEqualTo("media.coverimage.ffmpeg@1.0.0");
    }

    /**
     * Fail-closed control for cover: with no healthy implementation registered, the capability is
     * UNAVAILABLE and resolution is empty (the pending pin is gone, but the guard is not weakened).
     */
    @Test
    void coverImageWithoutAHealthyImplementationStaysUnavailableAndDoesNotResolve() {
        var projection = catalog();

        assertThat(availabilityOf(projection, "media.cover-image")).isEqualTo(Availability.UNAVAILABLE);
        assertThat(projection.resolveProviderBound("media.cover-image", "1.0")).isEmpty();
    }

    /**
     * Control: the availability gate must not blanket-empty resolution. A capability the catalog
     * reports AVAILABLE (a healthy provider whose declared types match the catalog contract) still
     * resolves to a provider binding exactly as before.
     */
    @Test
    void availableCapabilityStillResolvesToAProviderBinding() {
        var underTest = composition();
        registerProvider(underTest.registry(), "media.thumbnail.ffmpeg", "media.thumbnail",
                "Artifact", "Artifact");

        assertThat(availabilityOf(underTest.projection(), "media.thumbnail"))
                .isEqualTo(Availability.AVAILABLE);
        assertThat(underTest.projection().resolveProviderBound("media.thumbnail", "1.0"))
                .isPresent()
                .get()
                .extracting(binding -> binding.providerRegistryReference())
                .isEqualTo("media.thumbnail.ffmpeg@1.0.0");
    }

    /**
     * COVER-THUMBNAIL-REBUILD-001 (action 1): with both platform contributions registered and
     * declaring the catalog Artifact contract, {@code media.cover-image} and {@code media.thumbnail}
     * are both reported AVAILABLE.
     */
    @Test
    void bothCoverAndThumbnailAreAvailableWhenTheirContributionsAreRegistered() {
        var underTest = composition();
        registerProvider(underTest.registry(), "media.coverimage.ffmpeg", "media.cover-image",
                "Artifact", "Artifact");
        registerProvider(underTest.registry(), "media.thumbnail.ffmpeg", "media.thumbnail",
                "Artifact", "Artifact");

        assertThat(availabilityOf(underTest.projection(), "media.cover-image"))
                .isEqualTo(Availability.AVAILABLE);
        assertThat(availabilityOf(underTest.projection(), "media.thumbnail"))
                .isEqualTo(Availability.AVAILABLE);
    }
}
