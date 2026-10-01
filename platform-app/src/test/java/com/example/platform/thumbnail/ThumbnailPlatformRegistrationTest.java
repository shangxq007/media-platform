package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistries;
import com.example.platform.extension.api.port.PluginRegistrationException;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COVER-THUMBNAIL-REBUILD-001 (action 1): the thumbnail slice's provider contribution is
 * registered in the platform capability registry, its execution-provider contract declares exactly
 * {@code media.thumbnail} (model A), and both fail closed on invalid or duplicate contributions.
 *
 * <p>Mirrors {@code CoverImagePlatformRegistrationTest}. No Spring context or database is needed:
 * the registration seam is the platform's own registry contract.
 */
class ThumbnailPlatformRegistrationTest {

    private static PluginRegistrationPort registry() {
        return PluginRegistries.standalone();
    }

    @Test
    void contributionIsDiscoverableByCapabilityAndByPluginIdentity() {
        PluginRegistrationPort registry = registry();
        try (ThumbnailPlatformRegistration registration = new ThumbnailPlatformRegistration(registry)) {
            assertThat(registration.registrationId()).isEqualTo(
                    ThumbnailPlatformProvider.PLUGIN_ID + "@" + ThumbnailPlatformProvider.PLUGIN_VERSION);

            assertThat(registry.findByPluginId(ThumbnailPlatformProvider.PLUGIN_ID))
                    .as("plugin identity is registered")
                    .hasValueSatisfying(descriptor -> {
                        assertThat(descriptor.pluginVersion())
                                .isEqualTo(ThumbnailContracts.PROVIDER_VERSION);
                        assertThat(descriptor.vendor()).isEqualTo(ThumbnailPlatformProvider.VENDOR);
                        assertThat(descriptor.capabilities())
                                .extracting(CapabilityDescriptor::capabilityId)
                                .containsExactly(ThumbnailContracts.CAPABILITY);
                    });

            var implementations = ((CapabilityRegistryPort) registry)
                    .findCapabilityImplementations(CapabilityId.of(ThumbnailContracts.CAPABILITY));
            assertThat(implementations).as("capability registry visibility").hasSize(1);
            assertThat(implementations.getFirst().pluginId())
                    .isEqualTo(ThumbnailPlatformProvider.PLUGIN_ID);
            assertThat(implementations.getFirst().contractVersion()).isEqualTo(ContractVersion.of(1, 0));

            assertThat(registry.findCapabilityCandidates(ThumbnailContracts.CAPABILITY, "1.0"))
                    .as("capability -> provider candidates")
                    .hasSize(1);
            assertThat(registry.findCapabilityCandidates(ThumbnailContracts.CAPABILITY, "2.0"))
                    .as("a different contract version has no candidate")
                    .isEmpty();
        }
    }

    @Test
    void providerDescriptorIsModelAConsistentWithTheSliceConstants() {
        var descriptor = ThumbnailPlatformProvider.DESCRIPTOR;
        assertThat(descriptor.providerId().value()).isEqualTo(ThumbnailContracts.PROVIDER);
        assertThat(descriptor.providerImplementationId().value())
                .isEqualTo(ThumbnailContracts.PROVIDER_IMPLEMENTATION);
        assertThat(descriptor.providerVersion().value()).isEqualTo(ThumbnailContracts.PROVIDER_VERSION);
        assertThat(descriptor.providerId().value()).doesNotContain(ThumbnailContracts.CAPABILITY);
        assertThat(descriptor.providerImplementationId().value())
                .doesNotContain(ThumbnailContracts.CAPABILITY);

        assertThat(ThumbnailPlatformProvider.EXECUTION_CONTRACT.capabilityContractReferences())
                .extracting(reference -> reference.capabilityId().value())
                .containsExactly(ThumbnailContracts.CAPABILITY);
        assertThat(ThumbnailPlatformProvider.CAPABILITY_PROFILE.supportDeclarations())
                .extracting(support -> support.capabilityId().value())
                .containsExactly(ThumbnailContracts.CAPABILITY);
        assertThat(ThumbnailPlatformProvider.PLUGIN_DESCRIPTOR.capabilities().getFirst()
                .capabilityContractVersion()).isEqualTo(ThumbnailContracts.CAPABILITY_VERSION);
    }

    /**
     * Explicit N:M proof at the platform capability authority: a single provider contribution may
     * declare several capabilities, and the registry must expose it under each of them. This is the
     * shape cover and thumbnail now share (a provider declares a capability list).
     */
    @Test
    void aProviderDeclaringSeveralCapabilitiesIsDiscoverableUnderEach() {
        PluginRegistrationPort registry = registry();
        registry.registerRuntime(new com.example.platform.extension.domain.PluginDescriptor(
                "media.multiframe.ffmpeg",
                "1.0.0",
                "1",
                "media-platform",
                List.of(
                        new CapabilityDescriptor(
                                ThumbnailContracts.CAPABILITY, ThumbnailContracts.CAPABILITY_VERSION,
                                "multiframe", "Artifact", "Artifact",
                                CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                        new CapabilityDescriptor(
                                CoverImageContractsRef.CAPABILITY, CoverImageContractsRef.CAPABILITY_VERSION,
                                "multiframe", "Artifact", "Artifact",
                                CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new com.example.platform.extension.domain.HandledObjectDescriptor(
                        "ExecutableTask", "1",
                        "com.example.platform.execution.taskgraph.ExecutableTask",
                        List.of("providerBindingPin"), List.of(),
                        com.example.platform.extension.domain.HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                com.example.platform.extension.domain.InvocationContract.syncOnlyDefault(),
                List.of(),
                new com.example.platform.extension.domain.ResourceRequirement(
                        1, 256, 50, 0, 1024L, 1024L, 60_000L, false, 8192, false, 60_000L),
                com.example.platform.extension.domain.PluginRuntimeRequirement.trustedInProcess(),
                com.example.platform.extension.domain.PluginGuarantee.noneDeclared()));

        CapabilityRegistryPort capabilities = (CapabilityRegistryPort) registry;
        assertThat(capabilities.findCapabilityImplementations(CapabilityId.of(ThumbnailContracts.CAPABILITY)))
                .hasSize(1);
        assertThat(capabilities.findCapabilityImplementations(
                CapabilityId.of(CoverImageContractsRef.CAPABILITY)))
                .as("the same provider is discoverable under its second capability")
                .hasSize(1);
        assertThat(registry.findCapabilityCandidates(ThumbnailContracts.CAPABILITY, "1.0")).hasSize(1);
        assertThat(registry.findCapabilityCandidates(CoverImageContractsRef.CAPABILITY, "1.0")).hasSize(1);
    }

    @Test
    void registrationIsLeaseScoped() {
        PluginRegistrationPort registry = registry();
        ThumbnailPlatformRegistration registration = new ThumbnailPlatformRegistration(registry);
        assertThat(registry.findByPluginId(ThumbnailPlatformProvider.PLUGIN_ID)).isPresent();

        registration.close();

        assertThat(registry.findByPluginId(ThumbnailPlatformProvider.PLUGIN_ID)).isEmpty();
        assertThat(((CapabilityRegistryPort) registry)
                .findCapabilityImplementations(CapabilityId.of(ThumbnailContracts.CAPABILITY)))
                .isEmpty();
    }

    @Test
    void registrationIsInertWhenNoPlatformCapabilityRegistryIsPresent() {
        ThumbnailPlatformRegistration registration =
                new ThumbnailPlatformRegistration((PluginRegistrationPort) null);
        assertThat(registration.registered()).isFalse();
        assertThat(registration.registrationId()).isNull();
        assertThat(registration.registry()).isNull();
        registration.close();
    }

    @Test
    void duplicateContributionIdentityFailsClosed() {
        PluginRegistrationPort registry = registry();
        try (ThumbnailPlatformRegistration first = new ThumbnailPlatformRegistration(registry)) {
            assertThat(first.registrationId()).isNotBlank();
            assertThatThrownBy(() -> new ThumbnailPlatformRegistration(registry))
                    .isInstanceOf(PluginRegistrationException.class);
            assertThat(registry.enumerate()).hasSize(1);
        }
    }

    /**
     * Constant indirection so this test does not import the cover slice's production constant while
     * still proving cross-capability N:M discovery with the real cover capability id.
     */
    private static final class CoverImageContractsRef {
        static final String CAPABILITY = "media.cover-image";
        static final String CAPABILITY_VERSION = "1.0";

        private CoverImageContractsRef() {}
    }
}
