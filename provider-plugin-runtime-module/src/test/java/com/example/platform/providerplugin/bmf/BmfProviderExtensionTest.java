package com.example.platform.providerplugin.bmf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.bmf.BmfCpuProvider;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ProviderBackendExecutionSupport;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * BMF-PROVIDER-INTEGRATION-001: BMF's eight declarations, its own capability profile/contract (the
 * frozen BMF constants are empty), and its declared-but-not-yet-integrated runtime.
 */
class BmfProviderExtensionTest {

    private static final BmfProviderExtension EXTENSION = new BmfProviderExtension();

    @Test
    void identityComesFromTheFrozenBmfConstants() {
        assertThat(EXTENSION.pluginId()).isEqualTo("media.bmf.cpu");
        assertThat(EXTENSION.pluginVersion()).isEqualTo(BmfCpuProvider.VERSION.value());
        assertThat(EXTENSION.providerDescriptor()).isSameAs(BmfCpuProvider.DESCRIPTOR);
        assertThat(EXTENSION.providerBindingPin()).isSameAs(BmfCpuProvider.BINDING);
        assertThat(EXTENSION.providerStaticCompatibility())
                .isSameAs(BmfCpuProvider.STATIC_COMPATIBILITY);
        assertThat(EXTENSION.providerHardwareRequirement())
                .contains(BmfCpuProvider.HARDWARE_REQUIREMENT);
        assertThat(EXTENSION.runtimeDependencyRequirements())
                .isEqualTo(BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS);
    }

    @Test
    void capabilityProfileAndContractAreDeclaredHereWithFourCapabilities() {
        // ProviderCapabilityProfile canonicalises its declarations by capability id.
        assertThat(EXTENSION.providerCapabilityProfile().supportDeclarations())
                .extracting(ProviderCapabilitySupport::capabilityId)
                .containsExactly(
                        CapabilityId.of("media.transcode"),
                        CapabilityId.of("render.composite"),
                        CapabilityId.of("render.output"),
                        CapabilityId.of("video.decode"));
        assertThat(EXTENSION.providerCapabilityProfile().supportDeclarations())
                .allSatisfy(support -> assertThat(support.contractVersionRange())
                        .isEqualTo(ContractVersionRange.exactly(ContractVersion.of(1, 0))));
        assertThat(EXTENSION.providerExecutionContract().capabilityContractReferences())
                .hasSize(4);
    }

    @Test
    void loweringStaysUnsupportedSoTheKernelRefusesBmf() {
        assertThat(EXTENSION.providerStaticCompatibility().loweringSupport())
                .isEqualTo(ProviderStaticCompatibility.LoweringSupport.UNSUPPORTED);
        assertThat(EXTENSION.providerStaticCompatibility().knowledge())
                .isEqualTo(ProviderStaticCompatibility.Knowledge.DECLARED);
    }

    @Test
    void sandboxBackendWorkerSupportAndResourceProfileAreDeclaredHonestly() {
        assertThat(EXTENSION.sandboxRequirement()).isEqualTo(SandboxRuntimeRequirement.REQUIRED);
        assertThat(EXTENSION.resourceProfile()).isEmpty();
        assertThat(EXTENSION.providerBackendExecutionSupport()).hasValueSatisfying(support -> {
            assertThat(support.providerBindingPin()).isEqualTo(BmfCpuProvider.BINDING);
            assertThat(support.knowledge())
                    .isEqualTo(ProviderBackendExecutionSupport.Knowledge.DECLARED);
            assertThat(support.supportedBackends())
                    .containsExactly(ExecutionBackend.NATIVE_PULL_WORKER);
        });
        assertThat(EXTENSION.workerRuntimeSupportRequirement().providerBindingPin())
                .isEqualTo(BmfCpuProvider.BINDING);
        assertThat(EXTENSION.workerRuntimeSupportRequirement().requiredRuntimeKind())
                .isEqualTo(RuntimeLifecycleKind.EPHEMERAL_TASK);
        assertThat(EXTENSION.workerRuntimeSupportRequirement().supportIdentifier())
                .isEqualTo(RuntimeSupportIdentifier.of("bmf.cpu.v1"));
    }

    @Test
    void pluginDescriptorDeclaresTheTranscodeCapabilityAndTheThreePermissions() {
        assertThat(EXTENSION.pluginDescriptor().capabilities()).hasSize(1);
        assertThat(EXTENSION.pluginDescriptor().capabilities().getFirst().capabilityId())
                .isEqualTo("media.transcode");
        assertThat(EXTENSION.pluginDescriptor().permissions())
                .extracting(permission -> permission.permissionId())
                .containsExactly("bmf.cpu", "bmf.ffmpeg", "bmf.python");
    }

    @Test
    void creatingARuntimeBindingFailsClosedUntilBmfIsIntegrated() {
        assertThatThrownBy(() -> EXTENSION.createRuntimeBinding(new ProviderPluginRuntimeContext(
                Path.of("/usr/bin/bmf"), Path.of("/tmp/bmf-workspace"),
                Duration.ofMinutes(1), 4096, SandboxCancellation.never())))
                .isInstanceOf(ProviderNativeExecutionFailure.class)
                .satisfies(failure -> assertThat(
                        ((ProviderNativeExecutionFailure) failure).code())
                        .isEqualTo(ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN))
                .hasMessageContaining("BMF runtime not yet integrated");
    }
}
