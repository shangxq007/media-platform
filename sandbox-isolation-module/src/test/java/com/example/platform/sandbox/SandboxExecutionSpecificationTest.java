package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SandboxExecutionSpecificationTest {

    @TempDir Path temp;

    @Test
    void resolves_exact_typed_policy_when_every_mandatory_capability_is_advertised() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path input = temp.resolve("input.txt");
        java.nio.file.Files.createDirectory(workspace);
        java.nio.file.Files.writeString(input, "input");
        SandboxExecutionRequirement requirement = requirement(workspace, input);
        SandboxRuntimeCapabilities capabilities = SandboxRuntimeCapabilities.available(
                Set.of(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.WALL_CLOCK_TIMEOUT,
                        SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.NETWORK_NONE,
                        SandboxCapability.ENVIRONMENT_CLEARING,
                        SandboxCapability.BOUNDED_CAPTURE,
                        SandboxCapability.CPU_COUNT_LIMIT,
                        SandboxCapability.MEMORY_LIMIT,
                        SandboxCapability.PROCESS_COUNT_LIMIT,
                        SandboxCapability.OPEN_FILE_LIMIT,
                        SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                        SandboxCapability.OUTPUT_STORAGE_LIMIT,
                        SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL, SandboxCapability.DEVICE_NONE));

        SandboxResolution resolution = SandboxExecutionResolver.resolve(requirement, capabilities);

        assertThat(resolution).isInstanceOf(SandboxResolution.Resolved.class);
        EffectiveSandboxExecutionSpecification effective =
                ((SandboxResolution.Resolved) resolution).specification();
        assertThat(effective.process().executable()).isEqualTo("/usr/bin/env");
        assertThat(effective.network()).isEqualTo(NetworkPolicy.none());
        assertThat(effective.devices().grantedDeviceReferences()).isEmpty();
        assertThat(effective.privilege()).isEqualTo(PrivilegePolicy.unprivileged());
    }

    @Test
    void mandatory_unavailable_capability_fails_closed_with_typed_failure() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path input = temp.resolve("input.txt");
        java.nio.file.Files.createDirectory(workspace);
        java.nio.file.Files.writeString(input, "input");

        SandboxResolution resolution = SandboxExecutionResolver.resolve(
                requirement(workspace, input),
                SandboxRuntimeCapabilities.available(Set.of(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.WALL_CLOCK_TIMEOUT,
                        SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.ENVIRONMENT_CLEARING,
                        SandboxCapability.BOUNDED_CAPTURE,
                        SandboxCapability.CPU_COUNT_LIMIT,
                        SandboxCapability.MEMORY_LIMIT,
                        SandboxCapability.PROCESS_COUNT_LIMIT,
                        SandboxCapability.OPEN_FILE_LIMIT,
                        SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                        SandboxCapability.OUTPUT_STORAGE_LIMIT,
                        SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL, SandboxCapability.DEVICE_NONE)));

        assertThat(resolution).isEqualTo(new SandboxResolution.Rejected(
                SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_CAPABILITY_UNSUPPORTED,
                        "mandatory capability is not advertised",
                        Set.of(SandboxCapability.NETWORK_NONE))));
    }

    @Test
    void unavailable_runtime_capability_evidence_fails_closed() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path input = temp.resolve("input.txt");
        java.nio.file.Files.createDirectory(workspace);
        java.nio.file.Files.writeString(input, "input");

        SandboxResolution resolution = SandboxExecutionResolver.resolve(
                requirement(workspace, input),
                SandboxRuntimeCapabilities.unavailable("probe-failed"));

        assertThat(resolution).isEqualTo(new SandboxResolution.Rejected(SandboxFailure.of(
                SandboxFailureCode.SANDBOX_UNAVAILABLE,
                "sandbox runtime is unavailable", Set.of())));
    }

    @Test
    void default_policies_deny_network_privilege_and_devices() {
        assertThat(NetworkPolicy.none().endpoints()).isEmpty();
        assertThat(PrivilegePolicy.unprivileged().privileged()).isFalse();
        assertThat(PrivilegePolicy.unprivileged().hostNamespaces()).isFalse();
        assertThat(PrivilegePolicy.unprivileged().hostSockets()).isFalse();
        assertThat(DeviceExposurePolicy.none().grantedDeviceReferences()).isEmpty();
    }

    @Test
    void endpoint_scope_and_device_grants_require_explicit_capability() throws Exception {
        Path workspace = temp.resolve("workspace");
        java.nio.file.Files.createDirectory(workspace);
        Path input = temp.resolve("input.txt");
        java.nio.file.Files.writeString(input, "input");
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                process(), filesystem(workspace, input),
                NetworkPolicy.endpoints(Set.of(NetworkEndpoint.tcp("example.invalid", 443))),
                EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.boundedDefaults(),
                DeviceExposurePolicy.granted(Set.of("gpu-1")));

        SandboxResolution resolution = SandboxExecutionResolver.resolve(
                requirement,
                SandboxRuntimeCapabilities.available(Set.of(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.WALL_CLOCK_TIMEOUT,
                        SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.ENVIRONMENT_CLEARING,
                        SandboxCapability.BOUNDED_CAPTURE,
                        SandboxCapability.CPU_COUNT_LIMIT,
                        SandboxCapability.MEMORY_LIMIT,
                        SandboxCapability.PROCESS_COUNT_LIMIT,
                        SandboxCapability.OPEN_FILE_LIMIT,
                        SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                        SandboxCapability.OUTPUT_STORAGE_LIMIT,
                        SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL, SandboxCapability.DEVICE_NONE)));

        assertThat(resolution).isInstanceOf(SandboxResolution.Rejected.class);
        assertThat(((SandboxResolution.Rejected) resolution).failure().missingCapabilities())
                .containsExactlyInAnyOrder(
                        SandboxCapability.NETWORK_ENDPOINT_ALLOWLIST,
                        SandboxCapability.DEVICE_GRANTS);

        SandboxRuntimeCapabilities capable = SandboxRuntimeCapabilities.available(Set.of(
                SandboxCapability.PROCESS_TREE_CONTAINMENT, SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION, SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.CPU_COUNT_LIMIT, SandboxCapability.MEMORY_LIMIT,
                SandboxCapability.PROCESS_COUNT_LIMIT, SandboxCapability.OPEN_FILE_LIMIT,
                SandboxCapability.TEMPORARY_STORAGE_LIMIT, SandboxCapability.OUTPUT_STORAGE_LIMIT,
                SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL,
                SandboxCapability.NETWORK_ENDPOINT_ALLOWLIST, SandboxCapability.DEVICE_GRANTS));
        assertThat(SandboxExecutionResolver.resolve(requirement, capable))
                .isInstanceOf(SandboxResolution.Resolved.class);
    }

    @Test
    void privileged_or_host_exposure_is_rejected_even_when_other_mechanics_are_capable() throws Exception {
        Path workspace = java.nio.file.Files.createDirectory(temp.resolve("workspace"));
        Path input = java.nio.file.Files.writeString(temp.resolve("input.txt"), "input");
        SandboxExecutionRequirement unsafe = new SandboxExecutionRequirement(
                process(), filesystem(workspace, input), NetworkPolicy.none(),
                EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                new PrivilegePolicy(true, true, true, true), ResourceEnforcementLimits.boundedDefaults(),
                DeviceExposurePolicy.none());
        SandboxRuntimeCapabilities capable = SandboxRuntimeCapabilities.available(Set.of(
                SandboxCapability.PROCESS_TREE_CONTAINMENT, SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION, SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.NETWORK_NONE,
                SandboxCapability.ENVIRONMENT_CLEARING, SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.CPU_COUNT_LIMIT, SandboxCapability.MEMORY_LIMIT,
                SandboxCapability.PROCESS_COUNT_LIMIT, SandboxCapability.OPEN_FILE_LIMIT,
                SandboxCapability.TEMPORARY_STORAGE_LIMIT, SandboxCapability.OUTPUT_STORAGE_LIMIT,
                SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL,
                SandboxCapability.DEVICE_NONE));

        assertThat(((SandboxResolution.Rejected) SandboxExecutionResolver.resolve(unsafe, capable))
                .failure().code()).isEqualTo(SandboxFailureCode.PRIVILEGE_SETUP_FAILED);
    }

    @Test
    void each_mandatory_resource_limit_fails_closed_when_its_mechanic_is_not_advertised() throws Exception {
        Path workspace = java.nio.file.Files.createDirectory(temp.resolve("workspace"));
        Path input = java.nio.file.Files.writeString(temp.resolve("input.txt"), "input");
        SandboxRuntimeCapabilities captureOnly = SandboxRuntimeCapabilities.available(Set.of(
                SandboxCapability.PROCESS_TREE_CONTAINMENT, SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION, SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.NETWORK_NONE,
                SandboxCapability.ENVIRONMENT_CLEARING, SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.UNPRIVILEGED_EXECUTION, SandboxCapability.HOST_EXPOSURE_DENIAL, SandboxCapability.DEVICE_NONE));

        SandboxResolution.Rejected rejected = (SandboxResolution.Rejected)
                SandboxExecutionResolver.resolve(requirement(workspace, input), captureOnly);

        assertThat(rejected.failure().missingCapabilities()).containsExactlyInAnyOrder(
                SandboxCapability.CPU_COUNT_LIMIT,
                SandboxCapability.MEMORY_LIMIT,
                SandboxCapability.PROCESS_COUNT_LIMIT,
                SandboxCapability.OPEN_FILE_LIMIT,
                SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                SandboxCapability.OUTPUT_STORAGE_LIMIT);
    }

    @Test
    void secrets_are_redacted_and_value_is_not_equality_or_digest_material() {
        OpaqueSecretReference reference = OpaqueSecretReference.of("render-api-token");
        try (ScopedSecretValue one = ScopedSecretValue.resolved(reference, "API_TOKEN", "alpha".toCharArray());
                ScopedSecretValue two = ScopedSecretValue.resolved(reference, "API_TOKEN", "beta".toCharArray())) {
            assertThat(one).isEqualTo(two);
            assertThat(one.toString()).doesNotContain("alpha", "beta").contains("[REDACTED]");
            assertThat(one.semanticProjection()).isEqualTo(two.semanticProjection());
            assertThat(one.copyValue()).containsExactly('a', 'l', 'p', 'h', 'a');
        }
    }

    @Test
    void invalid_executable_shell_form_and_argv_are_rejected() {
        assertThatThrownBy(() -> ProcessRequirement.of(
                Set.of("/usr/bin/env"), "/bin/sh", List.of("-c", "echo no"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessRequirement.of(
                Set.of("/bin/sh"), "/bin/sh", List.of("-c", "echo no"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessRequirement.of(
                Set.of("/usr/bin/env"), "/usr/bin/env", List.of("bad\u0000arg"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SandboxExecutionRequirement requirement(Path workspace, Path input) {
        return new SandboxExecutionRequirement(
                process(), filesystem(workspace, input), NetworkPolicy.none(),
                EnvironmentPolicy.exact(Map.of("LANG", "C")), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.boundedDefaults(),
                DeviceExposurePolicy.none());
    }

    private ProcessRequirement process() {
        return ProcessRequirement.of(
                Set.of("/usr/bin/env"), "/usr/bin/env", List.of(), Duration.ofSeconds(2));
    }

    private FilesystemPolicy filesystem(Path workspace, Path input) {
        return FilesystemPolicy.exact(
                Set.of(input), workspace, workspace.resolve("tmp"), workspace.resolve("out"), workspace);
    }
}
