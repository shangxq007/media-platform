package com.example.platform.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Host-binary mechanics inside a private bubblewrap mount/PID/network/device namespace. */
@org.springframework.modulith.NamedInterface("API")
public final class BubblewrapSandboxProcessLauncher implements BoundedProcessLauncher {
    private static final String SANDBOX_WORKSPACE = "/workspace";
    private static final String SANDBOX_INPUTS = "/sandbox-inputs";
    private static final Duration TERMINATION_GRACE = Duration.ofMillis(500);
    private static final List<Path> SYSTEM_ROOTS = List.of(
            Path.of("/usr"), Path.of("/bin"), Path.of("/lib"), Path.of("/lib64"));
    private static final List<Path> FORBIDDEN_MOUNT_ROOTS = List.of(
            Path.of("/etc"), Path.of("/proc"), Path.of("/dev"), Path.of("/run"),
            Path.of("/var/run"));
    private static final Set<String> FORBIDDEN_PATH_NAMES = Set.of(
            ".aws", ".config", ".docker", ".gnupg", ".ssh", "docker.sock");

    private final Path executable;
    private final SandboxRuntimeCapabilities capabilities;

    BubblewrapSandboxProcessLauncher(Path executable, SandboxRuntimeCapabilities capabilities) {
        this.executable = executable.toAbsolutePath().normalize();
        this.capabilities = capabilities;
    }

    public SandboxRuntimeCapabilities capabilities() {
        return capabilities;
    }

    public SandboxExecutionResult launchResolved(
            SandboxExecutionRequirement requirement, SandboxCancellation cancellation)
            throws IOException {
        SandboxResolution resolution = SandboxExecutionResolver.resolve(requirement, capabilities);
        if (resolution instanceof SandboxResolution.Rejected rejected) {
            return setupFailure(requirement.filesystem().workingDirectory(), rejected.failure());
        }
        return launch(((SandboxResolution.Resolved) resolution).specification(), cancellation);
    }

    @Override
    public SandboxExecutionResult launch(
            EffectiveSandboxExecutionSpecification spec, SandboxCancellation cancellation)
            throws IOException {
        if (!capabilities.equals(spec.runtimeCapabilities())) {
            throw new IllegalArgumentException(
                    "bubblewrap launcher capability evidence differs from resolved specification");
        }
        Optional<SandboxFailure> filesystemFailure = prepareFilesystem(spec.filesystem());
        if (filesystemFailure.isPresent()) {
            return setupFailure(spec.filesystem().workingDirectory(), filesystemFailure.orElseThrow());
        }

        List<String> command;
        try {
            command = buildCommand(executable, spec);
        } catch (IOException | RuntimeException failure) {
            return setupFailure(spec.filesystem().workingDirectory(), SandboxFailure.of(
                    SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION,
                    "bubblewrap mount path policy is unsatisfied", Set.of()));
        }

        Instant launchedAt = Instant.now();
        Process process;
        try {
            process = BubblewrapProcess.start(command, Map.of());
        } catch (IOException failure) {
            return setupFailure(spec.filesystem().workingDirectory(), SandboxFailure.of(
                    SandboxFailureCode.PROCESS_LAUNCH_FAILED,
                    "bubblewrap process launch failed", Set.of()));
        }

        SandboxExecutionHandle handle = new SandboxExecutionHandle(process.pid(), launchedAt);
        CaptureReader stdout = new CaptureReader(
                process.getInputStream(), spec.resources().captureBytes());
        CaptureReader stderr = new CaptureReader(
                process.getErrorStream(), spec.resources().captureBytes());
        Thread outThread = Thread.ofVirtual().start(stdout);
        Thread errThread = Thread.ofVirtual().start(stderr);
        SandboxFailure primaryFailure = null;
        long deadline = System.nanoTime() + spec.process().timeout().toNanos();
        while (process.isAlive()) {
            if (cancellation.isCancellationRequested()) {
                primaryFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT,
                        "process cancelled", Set.of());
                break;
            }
            if (System.nanoTime() >= deadline) {
                primaryFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TIMEOUT,
                        "process exceeded wall-clock timeout", Set.of());
                break;
            }
            try {
                process.waitFor(20, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                primaryFailure = SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_RUNTIME_LOST,
                        "bubblewrap launcher interrupted", Set.of());
                break;
            }
        }

        LocalBoundedProcessLauncher.TreeTermination termination =
                LocalBoundedProcessLauncher.terminateTreeWithHandles(
                        process, TERMINATION_GRACE, List.of());
        boolean capturesComplete = closeAndJoinCaptures(stdout, outThread, stderr, errThread);
        SandboxCleanupObservation cleanup = cleanupObservation(
                process.pid(), termination.observation(), capturesComplete);
        OptionalInt exit = OptionalInt.empty();
        if (!process.isAlive()) {
            try {
                exit = OptionalInt.of(process.exitValue());
            } catch (IllegalThreadStateException ignored) {
            }
        }
        if (primaryFailure == null && exit.isPresent() && exit.getAsInt() != 0) {
            boolean setupRejected = stderr.capture().utf8().stripLeading().startsWith("bwrap:");
            primaryFailure = SandboxFailure.of(
                    setupRejected ? SandboxFailureCode.SANDBOX_SETUP_FAILED
                            : SandboxFailureCode.PROCESS_CRASHED,
                    setupRejected ? "bubblewrap rejected sandbox setup" : "process exited non-zero",
                    Set.of());
        }
        return new SandboxExecutionResult(exit, stdout.capture(), stderr.capture(),
                selectResultFailure(primaryFailure, cleanup),
                new SandboxExecutionObservation(handle,
                        spec.filesystem().workingDirectory().toRealPath(),
                        Duration.between(launchedAt, Instant.now()), cleanup));
    }

    static List<String> buildCommand(
            Path bubblewrap, EffectiveSandboxExecutionSpecification spec) throws IOException {
        FilesystemPolicy filesystem = spec.filesystem();
        List<Path> inputs = filesystem.readOnlyInputs().stream()
                .sorted(Comparator.comparing(Path::toString)).toList();
        List<String> command = new ArrayList<>();
        command.add(bubblewrap.toString());
        command.addAll(List.of("--unshare-all", "--die-with-parent", "--new-session"));
        for (Path systemRoot : SYSTEM_ROOTS) {
            if (Files.exists(systemRoot)) {
                command.addAll(List.of(
                        "--ro-bind", systemRoot.toRealPath().toString(), systemRoot.toString()));
            }
        }
        command.addAll(List.of("--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp",
                "--tmpfs", SANDBOX_INPUTS,
                "--ro-bind", filesystem.workspaceRoot().toRealPath().toString(), SANDBOX_WORKSPACE,
                "--bind", filesystem.temporaryRoot().toRealPath().toString(),
                translateWorkspacePath(filesystem, filesystem.temporaryRoot()),
                "--bind", filesystem.outputStagingRoot().toRealPath().toString(),
                translateWorkspacePath(filesystem, filesystem.outputStagingRoot())));
        for (int index = 0; index < inputs.size(); index++) {
            Path input = inputs.get(index);
            String destination = input.startsWith(filesystem.workspaceRoot())
                    ? translateWorkspacePath(filesystem, input)
                    : SANDBOX_INPUTS + "/input-" + index;
            command.addAll(List.of("--ro-bind", input.toRealPath().toString(), destination));
        }
        command.addAll(List.of("--remount-ro", SANDBOX_INPUTS, "--chdir",
                translateWorkspacePath(filesystem, filesystem.workingDirectory()), "--clearenv"));
        spec.environment().values().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> command.addAll(
                        List.of("--setenv", entry.getKey(), entry.getValue())));
        command.add(spec.process().executable());
        for (String argument : spec.process().arguments()) {
            command.add(translateArgument(filesystem, inputs, argument));
        }
        return List.copyOf(command);
    }

    private Optional<SandboxFailure> prepareFilesystem(FilesystemPolicy filesystem) {
        try {
            Path workspace = filesystem.workspaceRoot();
            if (isBroadOrSensitiveWorkspace(workspace)) return filesystemFailure();
            Files.createDirectories(workspace);
            Files.createDirectories(filesystem.temporaryRoot());
            Files.createDirectories(filesystem.outputStagingRoot());
            Files.createDirectories(filesystem.workingDirectory());
            if (!Files.isDirectory(workspace)
                    || FilesystemPathValidator.validateExactNoSymlink(workspace).isPresent()
                    || FilesystemPathValidator.validateWorkingDirectory(
                            workspace, filesystem.workingDirectory()).isPresent()
                    || FilesystemPathValidator.validateWithin(
                            workspace, filesystem.temporaryRoot()).isPresent()
                    || FilesystemPathValidator.validateExactNoSymlink(
                            filesystem.temporaryRoot()).isPresent()
                    || FilesystemPathValidator.validateOutput(
                            workspace, filesystem.outputStagingRoot()).isPresent()
                    || FilesystemPathValidator.validateExactNoSymlink(
                            filesystem.outputStagingRoot()).isPresent()) {
                return filesystemFailure();
            }
            for (Path input : filesystem.readOnlyInputs()) {
                if (!Files.exists(input) || isSensitivePath(input)
                        || FilesystemPathValidator.validateExactNoSymlink(input).isPresent()) {
                    return filesystemFailure();
                }
            }
            return Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return filesystemFailure();
        }
    }

    private static boolean isBroadOrSensitiveWorkspace(Path workspace) throws IOException {
        Path real = workspace.toAbsolutePath().normalize();
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (real.getNameCount() < 2 || real.equals(userHome) || userHome.startsWith(real)
                || isSensitivePath(real)) {
            return true;
        }
        return Files.exists(real.resolve(".git"));
    }

    private static boolean isSensitivePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (FORBIDDEN_MOUNT_ROOTS.stream()
                .anyMatch(root -> normalized.equals(root) || normalized.startsWith(root))) {
            return true;
        }
        for (Path segment : normalized) {
            if (FORBIDDEN_PATH_NAMES.contains(segment.toString())) return true;
        }
        return false;
    }

    private static Optional<SandboxFailure> filesystemFailure() {
        return Optional.of(SandboxFailure.of(
                SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION,
                "bubblewrap mount path or symlink policy is unsatisfied", Set.of()));
    }

    private static String translateArgument(
            FilesystemPolicy filesystem, List<Path> inputs, String argument) {
        int separator = argument.indexOf('=');
        if (separator > 0 && separator + 1 < argument.length()) {
            String translated = translateAbsolutePath(
                    filesystem, inputs, argument.substring(separator + 1));
            return argument.substring(0, separator + 1) + translated;
        }
        return translateAbsolutePath(filesystem, inputs, argument);
    }

    private static String translateAbsolutePath(
            FilesystemPolicy filesystem, List<Path> inputs, String value) {
        Path candidate;
        try {
            candidate = Path.of(value);
        } catch (RuntimeException invalid) {
            return value;
        }
        if (!candidate.isAbsolute()) return value;
        Path normalized = candidate.normalize();
        for (int index = 0; index < inputs.size(); index++) {
            Path input = inputs.get(index);
            if (normalized.equals(input)) {
                return input.startsWith(filesystem.workspaceRoot())
                        ? translateWorkspacePath(filesystem, input)
                        : SANDBOX_INPUTS + "/input-" + index;
            }
            if (Files.isDirectory(input) && normalized.startsWith(input)) {
                String root = input.startsWith(filesystem.workspaceRoot())
                        ? translateWorkspacePath(filesystem, input)
                        : SANDBOX_INPUTS + "/input-" + index;
                return root + "/" + input.relativize(normalized).toString().replace('\\', '/');
            }
        }
        if (normalized.startsWith(filesystem.workspaceRoot())) {
            return translateWorkspacePath(filesystem, normalized);
        }
        return value;
    }

    private static String translateWorkspacePath(FilesystemPolicy filesystem, Path path) {
        Path relative = filesystem.workspaceRoot().relativize(path);
        return relative.toString().isEmpty()
                ? SANDBOX_WORKSPACE
                : SANDBOX_WORKSPACE + "/" + relative.toString().replace('\\', '/');
    }

    private static SandboxCleanupObservation cleanupObservation(
            long processId, SandboxCleanupObservation localCleanup, boolean capturesComplete) {
        boolean processReaped = !localCleanup.survivors().contains(processId);
        boolean workloadContained = localCleanup.survivors().isEmpty();
        boolean completed = processReaped && workloadContained && capturesComplete;
        List<String> failures = new ArrayList<>();
        if (!processReaped) failures.add("bubblewrap process remains alive");
        if (!workloadContained) failures.add("bubblewrap workload descendants remain alive");
        if (!capturesComplete) failures.add("bubblewrap capture streams remain open");
        Optional<SandboxFailure> failure = completed
                ? Optional.empty()
                : Optional.of(SandboxFailure.of(SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                        String.join("; ", failures), Set.of()));
        return new SandboxCleanupObservation(
                completed, true, processReaped, workloadContained, capturesComplete,
                localCleanup.descendantsObserved(), localCleanup.survivors(), List.of(),
                OptionalLong.of(processId), "", "not-applicable", failure);
    }

    static Optional<SandboxFailure> selectResultFailure(
            SandboxFailure primaryFailure, SandboxCleanupObservation cleanup) {
        return primaryFailure == null ? cleanup.failure() : Optional.of(primaryFailure);
    }

    private static SandboxExecutionResult setupFailure(Path working, SandboxFailure failure) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(OptionalInt.empty(),
                new BoundedCapture(new byte[0], false), new BoundedCapture(new byte[0], false),
                Optional.of(failure),
                new SandboxExecutionObservation(new SandboxExecutionHandle(-1, now), working,
                        Duration.ZERO, new SandboxCleanupObservation(true, 0, List.of(), "")));
    }

    private static boolean closeAndJoinCaptures(
            CaptureReader stdout, Thread outThread, CaptureReader stderr, Thread errThread) {
        boolean outComplete = joinCapture(outThread, 250);
        boolean errComplete = joinCapture(errThread, 250);
        stdout.close();
        stderr.close();
        return (outComplete || joinCapture(outThread, 750))
                & (errComplete || joinCapture(errThread, 750));
    }

    private static boolean joinCapture(Thread thread, long timeoutMillis) {
        try {
            thread.join(timeoutMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    private static final class CaptureReader implements Runnable, AutoCloseable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile boolean truncated;

        private CaptureReader(InputStream input, long limit) {
            this.input = input;
            this.limit = Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = limit - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(read, remaining));
                    if (read > remaining) truncated = true;
                }
            } catch (IOException ignored) {
            }
        }

        private BoundedCapture capture() {
            return new BoundedCapture(bytes.toByteArray(), truncated);
        }

        @Override
        public void close() {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
