package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Convenience entry into the one canonical local launcher for infrastructure callers. */
@org.springframework.modulith.NamedInterface("API")
public final class LocalSandboxProcess {
    private LocalSandboxProcess() {}

    public static SandboxExecutionResult execute(
            List<String> command,
            Path workspace,
            Path workingDirectory,
            Set<Path> readOnlyInputs,
            Map<String, String> exactEnvironment,
            Duration timeout,
            long captureBytes,
            SandboxCancellation cancellation) throws IOException {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        List<String> exactCommand = new java.util.ArrayList<>(command);
        exactCommand.set(0, resolveExecutable(command.getFirst()));
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(Set.of(exactCommand.getFirst()), exactCommand.getFirst(),
                        exactCommand.subList(1, exactCommand.size()), timeout),
                FilesystemPolicy.exact(readOnlyInputs, workspace,
                        workspace.resolve(".sandbox-tmp"), workspace.resolve(".sandbox-output"), workingDirectory),
                NetworkPolicy.none(), EnvironmentPolicy.exact(exactEnvironment), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(captureBytes),
                DeviceExposurePolicy.none());
        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        if (detection.launcher().isPresent()) {
            return detection.launcher().orElseThrow().launchResolved(requirement, cancellation);
        }
        java.time.Instant now = java.time.Instant.now();
        SandboxFailure failure = SandboxFailure.of(SandboxFailureCode.SANDBOX_UNAVAILABLE,
                detection.diagnostic(), Set.of());
        return new SandboxExecutionResult(java.util.OptionalInt.empty(),
                new BoundedCapture(new byte[0], false), new BoundedCapture(new byte[0], false),
                java.util.Optional.of(failure),
                new SandboxExecutionObservation(new SandboxExecutionHandle(-1, now), workingDirectory,
                        Duration.ZERO, new SandboxCleanupObservation(true, 0, List.of(), "")));
    }

    private static String resolveExecutable(String executable) {
        Path candidate = Path.of(executable);
        List<Path> roots = List.of(Path.of("/usr/bin"), Path.of("/bin"));
        List<Path> candidates = candidate.isAbsolute()
                ? List.of(candidate.normalize())
                : roots.stream().map(root -> root.resolve(executable).normalize()).toList();
        for (Path exact : candidates) {
            try {
                Path real = exact.toRealPath();
                boolean fixedRoot = roots.stream().map(root -> {
                    try {
                        return root.toRealPath();
                    } catch (IOException failure) {
                        return root;
                    }
                }).anyMatch(real::startsWith);
                if (fixedRoot && java.nio.file.Files.isRegularFile(real)
                        && java.nio.file.Files.isExecutable(real)) return real.toString();
            } catch (IOException ignored) {
            }
        }
        throw new IllegalArgumentException(
                "executable is not present in the fixed host-binary allow roots: " + executable);
    }
}
