package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistries;
import com.example.platform.extension.api.port.PluginRegistrationException;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COVER-PROVIDER-PLATFORM-REGISTER-001: the cover slice's provider contribution is registered in
 * the platform capability registry, its execution-provider contract declares exactly the
 * {@code media.cover-image} capability (model A), and both fail closed on invalid or duplicate
 * contributions. No Spring context or database is needed: the registration seam is the platform's
 * own registry contract.
 */
class CoverImagePlatformRegistrationTest {

    private static PluginRegistrationPort registry() {
        return PluginRegistries.standalone();
    }

    @Test
    void contributionIsDiscoverableByCapabilityAndByPluginIdentity() {
        PluginRegistrationPort registry = registry();
        try (CoverImagePlatformRegistration registration = new CoverImagePlatformRegistration(registry)) {
            assertThat(registration.registrationId()).isEqualTo(
                    CoverImagePlatformProvider.PLUGIN_ID + "@" + CoverImagePlatformProvider.PLUGIN_VERSION);

            assertThat(registry.findByPluginId(CoverImagePlatformProvider.PLUGIN_ID))
                    .as("plugin identity is registered")
                    .hasValueSatisfying(descriptor -> {
                        assertThat(descriptor.pluginVersion())
                                .isEqualTo(CoverImageContracts.PROVIDER_VERSION);
                        assertThat(descriptor.vendor()).isEqualTo(CoverImagePlatformProvider.VENDOR);
                        assertThat(descriptor.capabilities())
                                .extracting(CapabilityDescriptor::capabilityId)
                                .containsExactly(CoverImageContracts.CAPABILITY);
                    });

            var implementations = ((CapabilityRegistryPort) registry)
                    .findCapabilityImplementations(CapabilityId.of(CoverImageContracts.CAPABILITY));
            assertThat(implementations).as("capability registry visibility").hasSize(1);
            assertThat(implementations.getFirst().pluginId())
                    .isEqualTo(CoverImagePlatformProvider.PLUGIN_ID);
            assertThat(implementations.getFirst().contractVersion()).isEqualTo(ContractVersion.of(1, 0));

            assertThat(registry.findCapabilityCandidates(CoverImageContracts.CAPABILITY, "1.0"))
                    .as("capability -> provider candidates")
                    .hasSize(1);
            assertThat(registry.findCapabilityCandidates(CoverImageContracts.CAPABILITY, "2.0"))
                    .as("a different contract version has no candidate")
                    .isEmpty();
        }
    }

    @Test
    void registrationIsLeaseScoped() {
        PluginRegistrationPort registry = registry();
        CoverImagePlatformRegistration registration = new CoverImagePlatformRegistration(registry);
        assertThat(registry.findByPluginId(CoverImagePlatformProvider.PLUGIN_ID)).isPresent();

        registration.close();

        assertThat(registry.findByPluginId(CoverImagePlatformProvider.PLUGIN_ID)).isEmpty();
        assertThat(((CapabilityRegistryPort) registry)
                .findCapabilityImplementations(CapabilityId.of(CoverImageContracts.CAPABILITY)))
                .isEmpty();
    }

    @Test
    void providerDescriptorIsModelAConsistentWithTheSliceConstants() {
        var descriptor = CoverImagePlatformProvider.DESCRIPTOR;
        assertThat(descriptor.providerId().value()).isEqualTo(CoverImageContracts.PROVIDER);
        assertThat(descriptor.providerImplementationId().value())
                .isEqualTo(CoverImageContracts.PROVIDER_IMPLEMENTATION);
        assertThat(descriptor.providerVersion().value()).isEqualTo(CoverImageContracts.PROVIDER_VERSION);
        // Model A: the provider family must not embed the capability identity.
        assertThat(descriptor.providerId().value())
                .as("provider identity is capability-independent")
                .doesNotContain(CoverImageContracts.CAPABILITY);
        assertThat(descriptor.providerImplementationId().value())
                .doesNotContain(CoverImageContracts.CAPABILITY);

        assertThat(CoverImagePlatformProvider.EXECUTION_CONTRACT.capabilityContractReferences())
                .extracting(reference -> reference.capabilityId().value())
                .containsExactly(CoverImageContracts.CAPABILITY);
        assertThat(CoverImagePlatformProvider.CAPABILITY_PROFILE.supportDeclarations())
                .extracting(support -> support.capabilityId().value())
                .containsExactly(CoverImageContracts.CAPABILITY);
        assertThat(CoverImagePlatformProvider.CAPABILITY_PROFILE.supportDeclarations().getFirst()
                .contractVersionRange().contains(ContractVersion.parse(CoverImageContracts.CAPABILITY_VERSION)))
                .as("declared contract range covers the slice contract version")
                .isTrue();

        assertThat(CoverImagePlatformProvider.BINDING.providerId())
                .isEqualTo(descriptor.providerId());
        assertThat(CoverImagePlatformProvider.PLUGIN_DESCRIPTOR.capabilities().getFirst()
                .capabilityContractVersion()).isEqualTo(CoverImageContracts.CAPABILITY_VERSION);
    }

    @Test
    void registrationIsInertWhenNoPlatformCapabilityRegistryIsPresent() {
        CoverImagePlatformRegistration registration = new CoverImagePlatformRegistration(
                (PluginRegistrationPort) null);
        assertThat(registration.registered()).isFalse();
        assertThat(registration.registrationId()).isNull();
        assertThat(registration.registry()).isNull();
        registration.close(); // idempotent by construction: there is no lease to retire
    }

    @Test
    void contributionDoesNotClaimCapabilitiesItDoesNotServe() {
        PluginRegistrationPort registry = registry();
        try (CoverImagePlatformRegistration ignored = new CoverImagePlatformRegistration(registry)) {
            assertThat(registry.findCapabilityCandidates("media.transcode", "1.0")).isEmpty();
            assertThat(registry.findCapabilityCandidates("media.thumbnail", "1.0")).isEmpty();
            assertThat(registry.enumerate()).hasSize(1);
        }
    }

    @Test
    void duplicateContributionIdentityFailsClosed() {
        PluginRegistrationPort registry = registry();
        try (CoverImagePlatformRegistration first = new CoverImagePlatformRegistration(registry)) {
            assertThat(first.registrationId()).isNotBlank();
            assertThatThrownBy(() -> new CoverImagePlatformRegistration(registry))
                    .isInstanceOf(PluginRegistrationException.class);
            // The first (valid) registration survives; the rejected one added nothing.
            assertThat(registry.enumerate()).hasSize(1);
        }
    }

    @Test
    void invalidDescriptorIsRejectedBeforeAnyRegistryMutation() {
        PluginRegistrationPort registry = registry();
        PluginDescriptor invalid = new PluginDescriptor(
                "media.cover-image.ffmpeg", // hyphens are illegal in a plugin id (PLG_001)
                CoverImagePlatformProvider.PLUGIN_VERSION,
                CoverImagePlatformProvider.PLATFORM_API_VERSION,
                CoverImagePlatformProvider.VENDOR,
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

        assertThatThrownBy(() -> new CoverImagePlatformRegistration(
                registry, invalid, CoverImagePlatformProvider.DESCRIPTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registrable");
        assertThat(registry.enumerate()).as("no partial registration").isEmpty();
    }
}
