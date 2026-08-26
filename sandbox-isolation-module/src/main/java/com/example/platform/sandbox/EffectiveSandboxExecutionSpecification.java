package com.example.platform.sandbox;

/** Immutable validated effective specification; only the fail-closed resolver can construct it. */
@org.springframework.modulith.NamedInterface("API")
public final class EffectiveSandboxExecutionSpecification {
    private final SandboxExecutionRequirement requirement;
    private final SandboxRuntimeCapabilities runtimeCapabilities;

    private EffectiveSandboxExecutionSpecification(
            SandboxExecutionRequirement requirement, SandboxRuntimeCapabilities runtimeCapabilities) {
        this.requirement = requirement;
        this.runtimeCapabilities = runtimeCapabilities;
    }

    static EffectiveSandboxExecutionSpecification resolved(
            SandboxExecutionRequirement requirement, SandboxRuntimeCapabilities runtimeCapabilities) {
        return new EffectiveSandboxExecutionSpecification(requirement, runtimeCapabilities);
    }

    public ProcessRequirement process() { return requirement.process(); }
    public FilesystemPolicy filesystem() { return requirement.filesystem(); }
    public NetworkPolicy network() { return requirement.network(); }
    public EnvironmentPolicy environment() { return requirement.environment(); }
    public SecretExposure secrets() { return requirement.secrets(); }
    public PrivilegePolicy privilege() { return requirement.privilege(); }
    public ResourceEnforcementLimits resources() { return requirement.resources(); }
    public DeviceExposurePolicy devices() { return requirement.devices(); }
    public SandboxRuntimeCapabilities runtimeCapabilities() { return runtimeCapabilities; }
}
