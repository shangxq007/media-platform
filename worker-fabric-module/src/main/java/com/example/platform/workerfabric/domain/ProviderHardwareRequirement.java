package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable implementation-local hardware/runtime declaration for technical CAN_RUN. */
public record ProviderHardwareRequirement(
        ProviderImplementationId providerImplementationId,
        CpuArchitecture cpuArchitecture,
        Optional<ProviderHardwareDeviceRequirement> deviceRequirement,
        List<String> requiredProviderBuildFeatures,
        List<String> requiredCodecOrFilterFeatures,
        List<String> requiredSandboxPermissions)
        implements Serializable {

    public ProviderHardwareRequirement {
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(cpuArchitecture, "cpuArchitecture");
        Objects.requireNonNull(deviceRequirement, "deviceRequirement");
        requiredProviderBuildFeatures = RuntimeDependencyNames.canonicalize(
                requiredProviderBuildFeatures, "requiredProviderBuildFeatures");
        requiredCodecOrFilterFeatures = RuntimeDependencyNames.canonicalize(
                requiredCodecOrFilterFeatures, "requiredCodecOrFilterFeatures");
        requiredSandboxPermissions = RuntimeDependencyNames.canonicalize(
                requiredSandboxPermissions, "requiredSandboxPermissions");
    }
}
