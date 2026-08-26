package com.example.platform.sandbox;

import java.util.EnumSet;
import java.util.Set;

@org.springframework.modulith.NamedInterface("API")
public final class SandboxExecutionResolver {
    private SandboxExecutionResolver() {}

    public static SandboxResolution resolve(
            SandboxExecutionRequirement requirement, SandboxRuntimeCapabilities runtime) {
        if (!runtime.available()) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_UNAVAILABLE, "sandbox runtime is unavailable", Set.of()));
        }
        EnumSet<SandboxCapability> mandatory = EnumSet.of(
                SandboxCapability.PROCESS_TREE_CONTAINMENT,
                SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.UNPRIVILEGED_EXECUTION,
                SandboxCapability.HOST_EXPOSURE_DENIAL);
        if (requirement.resources().cpuCount().isPresent()) mandatory.add(SandboxCapability.CPU_COUNT_LIMIT);
        if (requirement.resources().memoryBytes().isPresent()) mandatory.add(SandboxCapability.MEMORY_LIMIT);
        if (requirement.resources().processCount().isPresent()) mandatory.add(SandboxCapability.PROCESS_COUNT_LIMIT);
        if (requirement.resources().openFileCount().isPresent()) mandatory.add(SandboxCapability.OPEN_FILE_LIMIT);
        if (requirement.resources().temporaryBytes().isPresent()) {
            mandatory.add(SandboxCapability.TEMPORARY_STORAGE_LIMIT);
        }
        if (requirement.resources().outputBytes().isPresent()) {
            mandatory.add(SandboxCapability.OUTPUT_STORAGE_LIMIT);
        }
        mandatory.add(requirement.network().mode() == NetworkPolicy.Mode.NONE
                ? SandboxCapability.NETWORK_NONE : SandboxCapability.NETWORK_ENDPOINT_ALLOWLIST);
        if (!requirement.secrets().references().isEmpty()) mandatory.add(SandboxCapability.SECRET_INJECTION);
        mandatory.add(requirement.devices().grantedDeviceReferences().isEmpty()
                ? SandboxCapability.DEVICE_NONE : SandboxCapability.DEVICE_GRANTS);
        EnumSet<SandboxCapability> missing = EnumSet.copyOf(mandatory);
        missing.removeAll(runtime.capabilities());
        if (!missing.isEmpty()) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_CAPABILITY_UNSUPPORTED,
                    "mandatory capability is not advertised", Set.copyOf(missing)));
        }
        if (requirement.privilege().privileged() || requirement.privilege().rootUser()
                || requirement.privilege().hostNamespaces() || requirement.privilege().hostSockets()) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.PRIVILEGE_SETUP_FAILED,
                    "privileged and host exposure are denied", Set.of()));
        }
        boolean invalidInput = requirement.filesystem().readOnlyInputs().stream()
                .anyMatch(path -> FilesystemPathValidator.validateExactNoSymlink(path).isPresent());
        if (invalidInput || FilesystemPathValidator.validateWorkingDirectory(
                requirement.filesystem().workspaceRoot(), requirement.filesystem().workingDirectory()).isPresent()
                || FilesystemPathValidator.validateOutput(requirement.filesystem().workspaceRoot(),
                        requirement.filesystem().outputStagingRoot()).isPresent()) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION,
                    "filesystem path or symlink policy is unsatisfied", Set.of()));
        }
        return new SandboxResolution.Resolved(
                EffectiveSandboxExecutionSpecification.resolved(requirement, runtime));
    }
}
