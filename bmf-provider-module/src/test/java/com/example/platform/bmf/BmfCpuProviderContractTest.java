package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.RuntimeDependencyAbi;
import com.example.platform.workerfabric.domain.RuntimeDependencyCoordinate;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersion;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersionConstraint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BmfCpuProviderContractTest {

    @Test
    void freezes_exact_provider_identity_without_collapsing_runtime_evidence() {
        assertThat(BmfCpuProvider.PROVIDER_ID.value()).isEqualTo("bmf");
        assertThat(BmfCpuProvider.IMPLEMENTATION_ID.value()).isEqualTo("bmf.cpu.v1");
        assertThat(BmfCpuProvider.VERSION.value()).isEqualTo("1.2.0");
        assertThat(BmfCpuProvider.EXECUTION_CONTRACT_VERSION)
                .isEqualTo(ProviderExecutionContractVersion.of(1, 0));
        assertThat(BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE)
                .isEqualTo(ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)));

        assertThat(BmfCpuProvider.IMPLEMENTATION_ID.value())
                .doesNotContain("fingerprint", "runtime");
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
    void declares_only_the_bounded_sandboxed_cpu_video_support() {
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.knowledge())
                .isEqualTo(ProviderStaticCompatibility.Knowledge.DECLARED);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedArtifactRequirements()).hasSize(3);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedCodecs())
                .extracting(codec -> codec.value()).containsExactly("h264");
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedDeviceKinds())
                .containsExactly(com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderDeviceKind.CPU);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedRuntimeClasses()).hasSize(2);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedSandboxModes())
                .containsExactly(ProviderStaticCompatibility.SandboxMode.SANDBOXED);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedDeterminismClasses())
                .containsExactly(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC);
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.supportedBoundaryContracts()).isEmpty();
        assertThat(BmfCpuProvider.STATIC_COMPATIBILITY.loweringSupport())
                .isEqualTo(ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
    }

    @Test
    void declares_the_exact_b0_cpu_runtime_closure_with_h1_types() {
        assertThat(BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS)
                .extracting(requirement -> requirement.coordinate().value())
                .containsExactly("bmf", "ffmpeg", "numpy", "python");
        assertThat(BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS)
                .allSatisfy(requirement -> assertThat(requirement.providerImplementationId())
                        .isEqualTo(BmfCpuProvider.IMPLEMENTATION_ID));

        assertExactRequirement("bmf", "0.2.0", Optional.empty(),
                List.of("ffmpeg.enabled", "python.enabled"),
                List.of("ambient-module-authority.disabled", "cuda.disabled"));
        assertExactRequirement("ffmpeg", "4.3.7",
                Optional.of(RuntimeDependencyAbi.of("libavcodec.58")), List.of(), List.of());
        assertExactRequirement("python", "3.9.2", Optional.empty(), List.of(), List.of());
        assertExactRequirement("numpy", "1.19.5", Optional.empty(), List.of(), List.of());

        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.providerImplementationId())
                .isEqualTo(BmfCpuProvider.IMPLEMENTATION_ID);
        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.cpuArchitecture())
                .isEqualTo(CpuArchitecture.X86_64);
        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.deviceRequirement()).isEmpty();
        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.requiredProviderBuildFeatures())
                .containsExactly("bmf.cpu.enabled", "bmf.ffmpeg.enabled", "bmf.python.enabled");
        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.requiredCodecOrFilterFeatures()).isEmpty();
        assertThat(BmfCpuProvider.HARDWARE_REQUIREMENT.requiredSandboxPermissions())
                .containsExactly("ambient-module-authority.disabled");
    }

    private static void assertExactRequirement(
            String coordinate,
            String version,
            Optional<RuntimeDependencyAbi> abi,
            List<String> features,
            List<String> flags) {
        RuntimeDependencyRequirement requirement = BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS
                .stream()
                .filter(candidate -> candidate.coordinate()
                        .equals(RuntimeDependencyCoordinate.of(coordinate)))
                .findFirst()
                .orElseThrow();
        assertThat(requirement.versionConstraint())
                .isEqualTo(RuntimeDependencyVersionConstraint.exact(
                        RuntimeDependencyVersion.of(version)));
        assertThat(requirement.abiConstraint()).isEqualTo(abi);
        assertThat(requirement.requiredFeatures()).containsExactlyElementsOf(features);
        assertThat(requirement.requiredBuildRuntimeFlags()).containsExactlyElementsOf(flags);
    }
}
