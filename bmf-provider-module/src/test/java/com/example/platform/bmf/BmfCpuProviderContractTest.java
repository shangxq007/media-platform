package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import org.junit.jupiter.api.Test;

class BmfCpuProviderContractTest {

    /** Accepted B0 runtime evidence identity; deliberately not B1 provider identity. */
    private static final String ACCEPTED_B0_RUNTIME_FINGERPRINT =
            "sha256:5ad7e1e40dd3cfa453960b829a6f61de7216c956638d06e7ad2cefe4be96dfd5";

    @Test
    void freezes_exact_provider_identity_without_collapsing_runtime_evidence() {
        assertThat(BmfCpuProvider.PROVIDER_ID.value()).isEqualTo("bmf");
        assertThat(BmfCpuProvider.IMPLEMENTATION_ID.value()).isEqualTo("bmf.cpu.v1");
        assertThat(BmfCpuProvider.VERSION.value()).isEqualTo("1.0.0");
        assertThat(BmfCpuProvider.EXECUTION_CONTRACT_VERSION)
                .isEqualTo(ProviderExecutionContractVersion.of(1, 0));
        assertThat(BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE)
                .isEqualTo(ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)));

        assertThat(BmfCpuProvider.PROVIDER_ID.value())
                .isNotEqualTo(ACCEPTED_B0_RUNTIME_FINGERPRINT);
        assertThat(BmfCpuProvider.IMPLEMENTATION_ID.value())
                .isNotEqualTo(ACCEPTED_B0_RUNTIME_FINGERPRINT)
                .doesNotContain("fingerprint", "runtime");
        assertThat(BmfCpuProvider.VERSION.value())
                .isNotEqualTo(ACCEPTED_B0_RUNTIME_FINGERPRINT);
    }

    @Test
    void descriptor_contract_profile_and_binding_are_exactly_consistent_and_empty() {
        assertThat(BmfCpuProvider.DESCRIPTOR.providerId()).isEqualTo(BmfCpuProvider.PROVIDER_ID);
        assertThat(BmfCpuProvider.DESCRIPTOR.providerImplementationId())
                .isEqualTo(BmfCpuProvider.IMPLEMENTATION_ID);
        assertThat(BmfCpuProvider.DESCRIPTOR.providerVersion()).isEqualTo(BmfCpuProvider.VERSION);
        assertThat(BmfCpuProvider.DESCRIPTOR.providerExecutionContractVersion())
                .isEqualTo(BmfCpuProvider.EXECUTION_CONTRACT_VERSION);
        assertThat(BmfCpuProvider.DESCRIPTOR.providerCapabilityProfileReference())
                .isEqualTo(BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE);

        assertThat(BmfCpuProvider.EXECUTION_CONTRACT.contractVersion())
                .isEqualTo(BmfCpuProvider.EXECUTION_CONTRACT_VERSION);
        assertThat(BmfCpuProvider.EXECUTION_CONTRACT.capabilityContractReferences()).isEmpty();
        assertThat(BmfCpuProvider.CAPABILITY_PROFILE.reference())
                .isEqualTo(BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE);
        assertThat(BmfCpuProvider.CAPABILITY_PROFILE.supportDeclarations()).isEmpty();

        assertThat(BmfCpuProvider.BINDING.providerId()).isEqualTo(BmfCpuProvider.PROVIDER_ID);
        assertThat(BmfCpuProvider.BINDING.providerImplementationId())
                .isEqualTo(BmfCpuProvider.IMPLEMENTATION_ID);
        assertThat(BmfCpuProvider.BINDING.providerVersion()).isEqualTo(BmfCpuProvider.VERSION);
        assertThat(BmfCpuProvider.BINDING.providerExecutionContractVersion())
                .isEqualTo(BmfCpuProvider.EXECUTION_CONTRACT_VERSION);
        assertThat(BmfCpuProvider.BINDING.providerCapabilityProfileVersionOrDigest())
                .isEqualTo(BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE);
        assertThat(BmfCpuProvider.BINDING.capabilityImplementationPins()).isEmpty();
    }

    @Test
    void declares_no_static_support_and_cannot_lower() {
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.knowledge())
                .isEqualTo(ProviderStaticCompatibility.Knowledge.DECLARED);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedArtifactRequirements()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedCodecs()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedDeviceKinds()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedRuntimeClasses()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedSandboxModes()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedDeterminismClasses()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedBoundaryContracts()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.loweringSupport())
                .isEqualTo(ProviderStaticCompatibility.LoweringSupport.UNSUPPORTED);
    }
}
