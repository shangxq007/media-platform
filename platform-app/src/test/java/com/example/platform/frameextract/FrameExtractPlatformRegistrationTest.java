package com.example.platform.frameextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.coverimage.CoverImageContracts;
import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistries;
import com.example.platform.extension.api.port.PluginRegistrationException;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.thumbnail.ThumbnailContracts;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The single capability-neutral ffmpeg frame-extract contribution is registered in the platform
 * capability registry, its execution-provider contract declares exactly the {@code media.cover-image}
 * and {@code media.thumbnail} capabilities (model A, N:M), and the registration fails closed on
 * invalid or duplicate contributions.
 *
 * <p>This is the merged successor of {@code CoverImagePlatformRegistrationTest} and
 * {@code ThumbnailPlatformRegistrationTest}: cover and thumbnail are no longer two single-capability
 * contributions but one contribution whose capability list is
 * {@code [media.cover-image@1.0, media.thumbnail@1.0]}. No Spring context or database is needed: the
 * registration seam is the platform's own registry contract.
 */
class FrameExtractPlatformRegistrationTest {

    private static PluginRegistrationPort registry() {
        return PluginRegistries.standalone();
    }

    @Test
    void oneContributionIsDiscoverableByBothCapabilitiesAndByPluginIdentity() {
        PluginRegistrationPort registry = registry();
        try (FrameExtractPlatformRegistration registration =
                new FrameExtractPlatformRegistration(registry)) {
            assertThat(registration.registrationId()).isEqualTo(
                    FrameExtractPlatformProvider.PLUGIN_ID + "@"
                            + FrameExtractPlatformProvider.PLUGIN_VERSION);

            assertThat(registry.findByPluginId(FrameExtractPlatformProvider.PLUGIN_ID))
                    .as("contribution identity is registered once")
                    .hasValueSatisfying(descriptor -> {
                        assertThat(descriptor.pluginVersion())
                                .isEqualTo(CoverImageContracts.PROVIDER_VERSION);
                        assertThat(descriptor.vendor()).isEqualTo(FrameExtractPlatformProvider.VENDOR);
                        assertThat(descriptor.capabilities())
                                .extracting(CapabilityDescriptor::capabilityId)
                                .containsExactlyInAnyOrder(
                                        CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
                    });

            CapabilityRegistryPort capabilities = (CapabilityRegistryPort) registry;
            for (String capabilityId : FrameExtractExecutionAdapter.CAPABILITIES) {
                var implementations =
                        capabilities.findCapabilityImplementations(CapabilityId.of(capabilityId));
                assertThat(implementations)
                        .as("capability registry visibility for %s", capabilityId)
                        .hasSize(1);
                assertThat(implementations.getFirst().pluginId())
                        .isEqualTo(FrameExtractPlatformProvider.PLUGIN_ID);
                assertThat(implementations.getFirst().contractVersion())
                        .isEqualTo(ContractVersion.of(1, 0));

                assertThat(registry.findCapabilityCandidates(capabilityId, "1.0"))
                        .as("capability -> provider candidates for %s", capabilityId)
                        .hasSize(1);
                assertThat(registry.findCapabilityCandidates(capabilityId, "2.0"))
                        .as("a different contract version has no candidate for %s", capabilityId)
                        .isEmpty();
            }
        }
    }

    @Test
    void registrationIsLeaseScopedForEveryDeclaredCapability() {
        PluginRegistrationPort registry = registry();
        FrameExtractPlatformRegistration registration = new FrameExtractPlatformRegistration(registry);
        assertThat(registry.findByPluginId(FrameExtractPlatformProvider.PLUGIN_ID)).isPresent();

        registration.close();

        assertThat(registry.findByPluginId(FrameExtractPlatformProvider.PLUGIN_ID)).isEmpty();
        CapabilityRegistryPort capabilities = (CapabilityRegistryPort) registry;
        for (String capabilityId : FrameExtractExecutionAdapter.CAPABILITIES) {
            assertThat(capabilities.findCapabilityImplementations(CapabilityId.of(capabilityId)))
                    .as("retiring the one lease retires %s too", capabilityId)
                    .isEmpty();
        }
    }

    @Test
    void providerDescriptorAndContributionIdentityAreCapabilityNeutral() {
        var descriptor = FrameExtractPlatformProvider.DESCRIPTOR;
        assertThat(descriptor.providerId().value()).isEqualTo(CoverImageContracts.PROVIDER);
        assertThat(descriptor.providerImplementationId().value())
                .isEqualTo(CoverImageContracts.PROVIDER_IMPLEMENTATION);
        assertThat(descriptor.providerVersion().value()).isEqualTo(CoverImageContracts.PROVIDER_VERSION);
        // Model A: the provider family and implementation must not embed either capability identity.
        assertThat(descriptor.providerId().value())
                .as("provider family is capability-independent")
                .doesNotContain(CoverImageContracts.CAPABILITY)
                .doesNotContain(ThumbnailContracts.CAPABILITY);
        assertThat(descriptor.providerImplementationId().value())
                .doesNotContain(CoverImageContracts.CAPABILITY)
                .doesNotContain(ThumbnailContracts.CAPABILITY);

        // The contribution id names the frame-extract contribution, never a capability.
        assertThat(FrameExtractPlatformProvider.PLUGIN_ID)
                .doesNotContain(CoverImageContracts.CAPABILITY)
                .doesNotContain(ThumbnailContracts.CAPABILITY)
                .doesNotContain("cover")
                .doesNotContain("thumbnail");

        assertThat(FrameExtractPlatformProvider.EXECUTION_CONTRACT.capabilityContractReferences())
                .extracting(reference -> reference.capabilityId().value())
                .containsExactlyInAnyOrder(
                        CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        assertThat(FrameExtractPlatformProvider.CAPABILITY_PROFILE.supportDeclarations())
                .extracting(support -> support.capabilityId().value())
                .containsExactlyInAnyOrder(
                        CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        assertThat(FrameExtractPlatformProvider.CAPABILITY_PROFILE.supportDeclarations())
                .allSatisfy(support -> assertThat(
                                support.contractVersionRange()
                                        .contains(ContractVersion.parse(
                                                CoverImageContracts.CAPABILITY_VERSION)))
                        .as("declared contract range covers the slice contract version")
                        .isTrue());

        assertThat(FrameExtractPlatformProvider.BINDING.providerId())
                .isEqualTo(descriptor.providerId());
        assertThat(FrameExtractPlatformProvider.PLUGIN_DESCRIPTOR.capabilities())
                .extracting(CapabilityDescriptor::capabilityContractVersion)
                .containsOnly(CoverImageContracts.CAPABILITY_VERSION);
    }

    @Test
    void bothCapabilitiesShareOneContributionAndOneProviderIdentity() {
        PluginRegistrationPort registry = registry();
        try (FrameExtractPlatformRegistration ignored =
                new FrameExtractPlatformRegistration(registry)) {
            CapabilityRegistryPort capabilities = (CapabilityRegistryPort) registry;
            String coverPlugin = capabilities
                    .findCapabilityImplementations(CapabilityId.of(CoverImageContracts.CAPABILITY))
                    .getFirst().pluginId();
            String thumbnailPlugin = capabilities
                    .findCapabilityImplementations(CapabilityId.of(ThumbnailContracts.CAPABILITY))
                    .getFirst().pluginId();

            assertThat(coverPlugin)
                    .as("one contribution serves both capabilities (N:M production topology)")
                    .isEqualTo(thumbnailPlugin)
                    .isEqualTo(FrameExtractPlatformProvider.PLUGIN_ID);
        }
    }

    @Test
    void registrationIsInertWhenNoPlatformCapabilityRegistryIsPresent() {
        FrameExtractPlatformRegistration registration = new FrameExtractPlatformRegistration(
                (PluginRegistrationPort) null);
        assertThat(registration.registered()).isFalse();
        assertThat(registration.registrationId()).isNull();
        assertThat(registration.registry()).isNull();
        registration.close(); // idempotent by construction: there is no lease to retire
    }

    @Test
    void contributionDoesNotClaimCapabilitiesItDoesNotServe() {
        PluginRegistrationPort registry = registry();
        try (FrameExtractPlatformRegistration ignored =
                new FrameExtractPlatformRegistration(registry)) {
            assertThat(registry.findCapabilityCandidates("media.transcode", "1.0")).isEmpty();
            assertThat(registry.findCapabilityCandidates("media.cover-preview", "1.0")).isEmpty();
            assertThat(registry.enumerate()).hasSize(1);
        }
    }

    @Test
    void duplicateContributionIdentityFailsClosed() {
        PluginRegistrationPort registry = registry();
        try (FrameExtractPlatformRegistration first = new FrameExtractPlatformRegistration(registry)) {
            assertThat(first.registrationId()).isNotBlank();
            assertThatThrownBy(() -> new FrameExtractPlatformRegistration(registry))
                    .isInstanceOf(PluginRegistrationException.class);
            // The first (valid) registration survives; the rejected one added nothing.
            assertThat(registry.enumerate()).hasSize(1);
        }
    }

    @Test
    void invalidDescriptorIsRejectedBeforeAnyRegistryMutation() {
        PluginRegistrationPort registry = registry();
        PluginDescriptor invalid = new PluginDescriptor(
                "media.frame-extract.ffmpeg", // hyphens are illegal in a plugin id (PLG_001)
                FrameExtractPlatformProvider.PLUGIN_VERSION,
                FrameExtractPlatformProvider.PLATFORM_API_VERSION,
                FrameExtractPlatformProvider.VENDOR,
                List.of(new CapabilityDescriptor(
                        CoverImageContracts.CAPABILITY,
                        CoverImageContracts.CAPABILITY_VERSION,
                        "cover",
                        "ExecutableTask",
                        "ProviderExecutionOutput",
                        CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor(
                        "ExecutableTask", "1",
                        "com.example.platform.execution.taskgraph.ExecutableTask",
                        List.of("providerBindingPin"), List.of(),
                        HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("ffmpeg.execute")),
                new com.example.platform.extension.domain.ResourceRequirement(
                        1, 256, 50, 0, 64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L, false, 4096, false, 60_000L),
                PluginRuntimeRequirement.trustedInProcess(),
                PluginGuarantee.noneDeclared());

        assertThatThrownBy(() -> new FrameExtractPlatformRegistration(
                registry, invalid, FrameExtractPlatformProvider.DESCRIPTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registrable");
        assertThat(registry.enumerate()).as("no partial registration").isEmpty();
    }
}
