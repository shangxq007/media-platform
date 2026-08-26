package com.example.platform.sandbox;

import java.util.Objects;

/** Typed execution-safety requirement, independent from trust and placement authorization. */
@org.springframework.modulith.NamedInterface("API")
public record SandboxExecutionRequirement(
        ProcessRequirement process,
        FilesystemPolicy filesystem,
        NetworkPolicy network,
        EnvironmentPolicy environment,
        SecretExposure secrets,
        PrivilegePolicy privilege,
        ResourceEnforcementLimits resources,
        DeviceExposurePolicy devices) {
    public SandboxExecutionRequirement {
        Objects.requireNonNull(process, "process"); Objects.requireNonNull(filesystem, "filesystem");
        Objects.requireNonNull(network, "network"); Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(secrets, "secrets"); Objects.requireNonNull(privilege, "privilege");
        Objects.requireNonNull(resources, "resources"); Objects.requireNonNull(devices, "devices");
    }
}
