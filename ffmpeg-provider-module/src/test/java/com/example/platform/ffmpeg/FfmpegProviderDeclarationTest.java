package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2a-2a-3a1: the contribution declares hardware, dependency and sandbox needs, and the interface
 * defaults fail closed.
 */
class FfmpegProviderDeclarationTest {

    @Test
    void interfaceDefaultsFailClosed() throws Exception {
        Method hardware = ProviderPluginContribution.class.getMethod("providerHardwareRequirement");
        Method dependencies = ProviderPluginContribution.class.getMethod("runtimeDependencyRequirements");
        Method sandbox = ProviderPluginContribution.class.getMethod("sandboxRequirement");

        assertThat(hardware.isDefault()).isTrue();
        assertThat(hardware.getReturnType()).isEqualTo(Optional.class);
        assertThat(dependencies.isDefault()).isTrue();
        assertThat(dependencies.getReturnType()).isEqualTo(List.class);
        // No "unknown" enum value exists: the fail-closed default is REQUIRED, never unsandboxed.
        assertThat(sandbox.isDefault()).isTrue();
        assertThat(sandbox.getReturnType()).isEqualTo(SandboxRuntimeRequirement.class);
    }

    @Test
    void ffmpegDeclaresHardwareNeeds() {
        Optional<ProviderHardwareRequirement> declared =
                new FfmpegProviderPluginContribution().providerHardwareRequirement();

        assertThat(declared).isPresent();
        ProviderHardwareRequirement hardware = declared.orElseThrow();
        assertThat(hardware.providerImplementationId()).isEqualTo(FfmpegCpuProvider.IMPLEMENTATION_ID);
        assertThat(hardware.cpuArchitecture()).isEqualTo(CpuArchitecture.X86_64);
        assertThat(hardware.deviceRequirement()).isEmpty();
        assertThat(hardware.requiredProviderBuildFeatures()).isEmpty();
        assertThat(hardware.requiredCodecOrFilterFeatures()).isEmpty();
        assertThat(hardware.requiredSandboxPermissions()).isEmpty();
    }

    @Test
    void ffmpegDeclaresNoExternalRuntimeDependencyAndRequiresSandboxing() {
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();

        assertThat(contribution.runtimeDependencyRequirements()).isEmpty();
        assertThat(contribution.sandboxRequirement()).isEqualTo(SandboxRuntimeRequirement.REQUIRED);
    }
}
