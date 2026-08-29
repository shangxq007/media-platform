package com.example.platform.workerfabric.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete successful probe evidence for a provider implementation on one runtime. */
public record ProviderHardwareAvailableEvidence(
        CpuArchitecture cpuArchitecture,
        List<String> providerBuildFeatures,
        List<String> codecOrFilterFeatures,
        List<String> sandboxPermissions,
        Optional<ProviderHardwareDeviceEvidence> deviceEvidence)
        implements ProviderHardwareProbeEvidence {

    public ProviderHardwareAvailableEvidence {
        Objects.requireNonNull(cpuArchitecture, "cpuArchitecture");
        providerBuildFeatures = RuntimeDependencyNames.canonicalize(
                providerBuildFeatures, "providerBuildFeatures");
        codecOrFilterFeatures = RuntimeDependencyNames.canonicalize(
                codecOrFilterFeatures, "codecOrFilterFeatures");
        sandboxPermissions = RuntimeDependencyNames.canonicalize(
                sandboxPermissions, "sandboxPermissions");
        Objects.requireNonNull(deviceEvidence, "deviceEvidence");
    }
}
