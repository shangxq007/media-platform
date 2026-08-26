package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Detects a rootless engine by executing the same hardening switches used by the adapter. */
@org.springframework.modulith.NamedInterface("API")
public final class ContainerSandboxCapabilityDetector {
    private static final Path PODMAN = Path.of("/usr/bin/podman");
    private static final Path DOCKER = Path.of("/usr/bin/docker");

    private ContainerSandboxCapabilityDetector() {}

    public static ContainerSandboxDetection detect(
            ContainerEnginePreference preference,
            String image,
            Optional<SandboxSecretResolver> secretResolver) {
        if (image == null || image.isBlank() || !image.contains(":")) {
            throw new IllegalArgumentException("container image must be explicitly version-pinned");
        }
        List<ContainerEngineConfiguration.Kind> requested = switch (preference) {
            case AUTO -> List.of(
                    ContainerEngineConfiguration.Kind.PODMAN,
                    ContainerEngineConfiguration.Kind.DOCKER);
            case PODMAN -> List.of(ContainerEngineConfiguration.Kind.PODMAN);
            case DOCKER -> List.of(ContainerEngineConfiguration.Kind.DOCKER);
        };
        boolean installed = false;
        List<String> diagnostics = new ArrayList<>();
        EnumSet<ContainerEngineConfiguration.Kind> probedKinds =
                EnumSet.noneOf(ContainerEngineConfiguration.Kind.class);
        Map<String, String> environment = engineEnvironment();
        for (ContainerEngineConfiguration.Kind requestedKind : requested) {
            Path executable = requestedKind == ContainerEngineConfiguration.Kind.PODMAN ? PODMAN : DOCKER;
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) continue;
            installed = true;
            Optional<ContainerEngineConfiguration.Kind> identified = engineKind(executable, environment);
            if (identified.isEmpty()) {
                diagnostics.add(executable + ": executable version behavior is not Podman or Docker");
                continue;
            }
            ContainerEngineConfiguration.Kind kind = identified.orElseThrow();
            if (!probedKinds.add(kind)) {
                diagnostics.add(executable + ": compatibility entrypoint identifies as "
                        + kind.name().toLowerCase(Locale.ROOT) + " already probed");
                continue;
            }
            ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                    kind, executable, image, environment);
            String rootlessFailure = rootlessFailure(configuration);
            if (rootlessFailure != null) {
                diagnostics.add(kind.name().toLowerCase() + ": " + rootlessFailure);
                continue;
            }
            EnforcementProbeResult enforcement = enforcementProbe(configuration);
            if (enforcement.failure() != null) {
                diagnostics.add(kind.name().toLowerCase() + ": " + enforcement.failure());
                continue;
            }
            EnumSet<SandboxCapability> capabilities = EnumSet.of(
                    SandboxCapability.PROCESS_TREE_CONTAINMENT,
                    SandboxCapability.WALL_CLOCK_TIMEOUT,
                    SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                    SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                    SandboxCapability.NETWORK_NONE,
                    SandboxCapability.ENVIRONMENT_CLEARING,
                    SandboxCapability.BOUNDED_CAPTURE,
                    SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                    SandboxCapability.UNPRIVILEGED_EXECUTION,
                    SandboxCapability.HOST_EXPOSURE_DENIAL,
                    SandboxCapability.DEVICE_NONE);
            capabilities.addAll(enforcement.resourceCapabilities());
            if (secretResolver.isPresent()) capabilities.add(SandboxCapability.SECRET_INJECTION);
            SandboxRuntimeCapabilities evidence = SandboxRuntimeCapabilities.detected(
                    capabilities, "rootless-" + kind.name().toLowerCase(), Instant.now());
            return new ContainerSandboxDetection(true,
                    Optional.of(new ContainerSandboxProcessLauncher(
                            configuration, evidence, secretResolver)),
                    "rootless " + kind.name().toLowerCase() + " enforcement probe passed; "
                            + "resource capabilities=" + enforcement.resourceCapabilities());
        }
        String diagnostic = diagnostics.isEmpty()
                ? "no selected Podman or Docker binary is installed"
                : String.join("; ", diagnostics);
        return new ContainerSandboxDetection(installed, Optional.empty(), diagnostic);
    }

    public static ContainerSandboxDetection detect(
            ContainerEnginePreference preference, String image) {
        return detect(preference, image, Optional.empty());
    }

    static Optional<ContainerEngineConfiguration.Kind> engineKind(
            Path executable, Map<String, String> environment) {
        ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                List.of(executable.toString(), "--version"), environment, Duration.ofSeconds(5));
        if (!result.succeeded()) return Optional.empty();
        String behavior = (result.stdout() + "\n" + result.stderr()).toLowerCase(Locale.ROOT);
        if (behavior.contains("podman")) {
            return Optional.of(ContainerEngineConfiguration.Kind.PODMAN);
        }
        if (behavior.contains("docker")) {
            return Optional.of(ContainerEngineConfiguration.Kind.DOCKER);
        }
        return Optional.empty();
    }

    private static String rootlessFailure(ContainerEngineConfiguration configuration) {
        List<String> command = new ArrayList<>();
        command.add(configuration.executable().toString());
        command.add("info");
        if (configuration.kind() == ContainerEngineConfiguration.Kind.PODMAN) {
            command.add("--format={{.Host.Security.Rootless}}");
        } else {
            command.add("--format={{json .SecurityOptions}}");
        }
        ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                command, configuration.engineEnvironment(), Duration.ofSeconds(5));
        if (!result.succeeded()) return "rootless info probe failed: " + result.diagnostic();
        String evidence = result.stdout().trim().toLowerCase();
        boolean rootless = configuration.kind() == ContainerEngineConfiguration.Kind.PODMAN
                ? evidence.equals("true")
                : evidence.contains("rootless");
        return rootless ? null : "engine is not rootless";
    }

    private static EnforcementProbeResult enforcementProbe(
            ContainerEngineConfiguration configuration) {
        String name = "media-platform-sandbox-probe-" + UUID.randomUUID();
        Path probeRoot = null;
        try {
            probeRoot = Files.createTempDirectory("media-platform-sandbox-probe-");
            Path workspace = Files.createDirectory(probeRoot.resolve("workspace"));
            Path temporary = Files.createDirectory(workspace.resolve("tmp"));
            Path output = Files.createDirectory(workspace.resolve("out"));
            Path input = Files.writeString(probeRoot.resolve("input"), "probe");
            ResourceEnforcementLimits limits = new ResourceEnforcementLimits(
                    Optional.empty(), OptionalLong.empty(), OptionalInt.empty(),
                    OptionalInt.empty(), OptionalLong.of(16L << 20),
                    OptionalLong.empty(), 4096);
            ProcessRequirement process = ProcessRequirement.of(
                    Set.of("/bin/touch"), "/bin/touch",
                    List.of(temporary.resolve("probe").toString(),
                            output.resolve("probe").toString()),
                    Duration.ofSeconds(30));
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    process, FilesystemPolicy.exact(
                            Set.of(input), workspace, temporary, output, workspace),
                    NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                    PrivilegePolicy.unprivileged(), limits, DeviceExposurePolicy.none());
            EffectiveSandboxExecutionSpecification specification =
                    EffectiveSandboxExecutionSpecification.resolved(
                            requirement, SandboxRuntimeCapabilities.unavailable("hardening-probe"));
            LinkedHashMap<String, String> processEnvironment =
                    new LinkedHashMap<>(configuration.engineEnvironment());
            List<String> command = ContainerSandboxProcessLauncher.buildCommand(
                    configuration, specification, name, "hardening-probe",
                    processEnvironment, List.of());
            ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                    command, processEnvironment, Duration.ofSeconds(45));
            if (!result.succeeded()) {
                return EnforcementProbeResult.failed(
                        "hardening probe failed: " + result.diagnostic());
            }
            if (!Files.isRegularFile(output.resolve("probe"))
                    || Files.exists(temporary.resolve("probe"))) {
                return EnforcementProbeResult.failed(
                        "hardening probe failed: writable workspace/output or ephemeral tmpfs "
                                + "was not enforced");
            }
            Path exactProbeRoot = probeRoot;
            Set<SandboxCapability> resources = detectResourceCapabilities(resourceLimits ->
                    resourceProbeSucceeded(configuration, exactProbeRoot, workspace, temporary,
                            output, input, resourceLimits));
            return EnforcementProbeResult.passed(resources);
        } catch (IOException | RuntimeException failure) {
            return EnforcementProbeResult.failed(
                    "hardening probe failed: " + failure.getClass().getSimpleName());
        } finally {
            forceRemove(configuration, name);
            deleteProbeTree(probeRoot);
        }
    }

    static Set<SandboxCapability> detectResourceCapabilities(
            Predicate<ResourceEnforcementLimits> exactProductionProbe) {
        EnumSet<SandboxCapability> capabilities = EnumSet.noneOf(SandboxCapability.class);
        probeResourceDimension(capabilities, SandboxCapability.CPU_COUNT_LIMIT,
                new ResourceEnforcementLimits(Optional.of(1.0), OptionalLong.empty(),
                        OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty(),
                        OptionalLong.empty(), 4096), exactProductionProbe);
        probeResourceDimension(capabilities, SandboxCapability.MEMORY_LIMIT,
                new ResourceEnforcementLimits(Optional.empty(), OptionalLong.of(64L << 20),
                        OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty(),
                        OptionalLong.empty(), 4096), exactProductionProbe);
        probeResourceDimension(capabilities, SandboxCapability.PROCESS_COUNT_LIMIT,
                new ResourceEnforcementLimits(Optional.empty(), OptionalLong.empty(),
                        OptionalInt.of(16), OptionalInt.empty(), OptionalLong.empty(),
                        OptionalLong.empty(), 4096), exactProductionProbe);
        probeResourceDimension(capabilities, SandboxCapability.OPEN_FILE_LIMIT,
                new ResourceEnforcementLimits(Optional.empty(), OptionalLong.empty(),
                        OptionalInt.empty(), OptionalInt.of(32), OptionalLong.empty(),
                        OptionalLong.empty(), 4096), exactProductionProbe);
        return Set.copyOf(capabilities);
    }

    private static void probeResourceDimension(
            EnumSet<SandboxCapability> capabilities,
            SandboxCapability capability,
            ResourceEnforcementLimits limits,
            Predicate<ResourceEnforcementLimits> exactProductionProbe) {
        if (exactProductionProbe.test(limits)) capabilities.add(capability);
    }

    private static boolean resourceProbeSucceeded(
            ContainerEngineConfiguration configuration,
            Path probeRoot,
            Path workspace,
            Path temporary,
            Path output,
            Path input,
            ResourceEnforcementLimits limits) {
        String name = "media-platform-resource-probe-" + UUID.randomUUID();
        try {
            ProcessRequirement process = ProcessRequirement.of(
                    Set.of("/bin/true"), "/bin/true", List.of(), Duration.ofSeconds(30));
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    process, FilesystemPolicy.exact(
                            Set.of(input), workspace, temporary, output, workspace),
                    NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                    PrivilegePolicy.unprivileged(), limits, DeviceExposurePolicy.none());
            EffectiveSandboxExecutionSpecification specification =
                    EffectiveSandboxExecutionSpecification.resolved(
                            requirement, SandboxRuntimeCapabilities.available(Set.of()));
            LinkedHashMap<String, String> processEnvironment =
                    new LinkedHashMap<>(configuration.engineEnvironment());
            List<String> command = ContainerSandboxProcessLauncher.buildCommand(
                    configuration, specification, name,
                    "resource-probe-" + probeRoot.getFileName(), processEnvironment, List.of());
            return ContainerEngineProcess.execute(
                    command, processEnvironment, Duration.ofSeconds(45)).succeeded();
        } catch (IOException | RuntimeException failure) {
            return false;
        } finally {
            forceRemove(configuration, name);
        }
    }

    static void forceRemove(ContainerEngineConfiguration configuration, String name) {
        ContainerEngineProcess.execute(
                configuration.immediateRemovalCommand(name),
                configuration.engineEnvironment(), Duration.ofSeconds(5));
    }

    private static Map<String, String> engineEnvironment() {
        LinkedHashMap<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("HOME", java.lang.System.getProperty("user.home", "/tmp"));
        try {
            Object uid = Files.getAttribute(Path.of("/proc/self"), "unix:uid");
            Path runtime = Path.of("/run/user", uid.toString());
            if (Files.isDirectory(runtime)) environment.put("XDG_RUNTIME_DIR", runtime.toString());
        } catch (Exception ignored) { }
        return Map.copyOf(environment);
    }

    private static void deleteProbeTree(Path probeRoot) {
        if (probeRoot == null) return;
        try {
            Path workspace = probeRoot.resolve("workspace");
            Files.deleteIfExists(workspace.resolve("out/probe"));
            Files.deleteIfExists(workspace.resolve("tmp/probe"));
            Files.deleteIfExists(workspace.resolve("out"));
            Files.deleteIfExists(workspace.resolve("tmp"));
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(probeRoot.resolve("input"));
            Files.deleteIfExists(probeRoot);
        } catch (IOException ignored) { }
    }

    private record EnforcementProbeResult(
            String failure, Set<SandboxCapability> resourceCapabilities) {
        private static EnforcementProbeResult failed(String failure) {
            return new EnforcementProbeResult(failure, Set.of());
        }

        private static EnforcementProbeResult passed(Set<SandboxCapability> capabilities) {
            return new EnforcementProbeResult(null, Set.copyOf(capabilities));
        }
    }
}
