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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Canonical local ProcessBuilder.start() boundary. */
@org.springframework.modulith.NamedInterface("API")
public final class LocalBoundedProcessLauncher implements BoundedProcessLauncher {
    private static final Duration TERMINATION_GRACE = Duration.ofMillis(250);
    private final SandboxRuntimeCapabilities capabilities;

    public LocalBoundedProcessLauncher() {
        this.capabilities = LocalSandboxCapabilityDetector.detect();
    }

    @Override
    public SandboxExecutionResult launch(
            EffectiveSandboxExecutionSpecification spec, SandboxCancellation cancellation)
            throws IOException {
        requireCapabilitiesMatch(spec);
        Path workspace = spec.filesystem().workspaceRoot();
        Path working = spec.filesystem().workingDirectory();
        if (FilesystemPathValidator.validateWorkingDirectory(workspace, working).isPresent()) {
            return setupFailure(spec, SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION,
                    "working directory escapes approved workspace");
        }
        Files.createDirectories(spec.filesystem().temporaryRoot());
        Files.createDirectories(spec.filesystem().outputStagingRoot());
        List<String> effectiveCommand = new ArrayList<>();
        if (spec.network().mode() == NetworkPolicy.Mode.ENDPOINT_ALLOWLIST) {
            return setupFailure(spec, SandboxFailureCode.NETWORK_POLICY_VIOLATION,
                    "local endpoint allowlist enforcement is unavailable");
        }
        effectiveCommand.addAll(spec.process().command());
        ProcessBuilder builder = new ProcessBuilder(effectiveCommand);
        builder.directory(working.toFile());
        builder.environment().clear();
        builder.environment().putAll(spec.environment().values());
        builder.redirectErrorStream(false);

        Instant launchedAt = Instant.now();
        Process process;
        try {
            process = builder.start();
        } catch (IOException failure) {
            return setupFailure(spec, SandboxFailureCode.PROCESS_LAUNCH_FAILED,
                    "process launch failed");
        }
        SandboxExecutionHandle handle = new SandboxExecutionHandle(process.pid(), launchedAt);
        CaptureReader stdout = new CaptureReader(process.getInputStream(), spec.resources().captureBytes());
        CaptureReader stderr = new CaptureReader(process.getErrorStream(), spec.resources().captureBytes());
        Thread outThread = Thread.ofVirtual().start(stdout);
        Thread errThread = Thread.ofVirtual().start(stderr);
        SandboxFailure processFailure = null;
        long deadline = System.nanoTime() + spec.process().timeout().toNanos();
        while (process.isAlive()) {
            if (cancellation.isCancellationRequested()) {
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT, "process cancelled", Set.of());
                break;
            }
            if (System.nanoTime() >= deadline) {
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TIMEOUT,
                        "process exceeded wall-clock timeout", Set.of());
                break;
            }
            try {
                process.waitFor(20, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_RUNTIME_LOST, "launcher interrupted", Set.of());
                break;
            }
        }
        SandboxCleanupObservation cleanup = terminateTree(process);
        boolean capturesComplete = closeAndJoinCaptures(stdout, outThread, stderr, errThread);
        if (!capturesComplete) {
            String failureMessage = cleanup.failureMessage();
            if (!failureMessage.isBlank()) failureMessage += "; ";
            failureMessage += "local capture streams remain open";
            cleanup = new SandboxCleanupObservation(
                    false, cleanup.namedContainerRemoved(), cleanup.engineClientReaped(),
                    cleanup.workloadProcessesContained(), false,
                    cleanup.descendantsObserved(), cleanup.survivors(),
                    cleanup.detachedEngineHelpers(),
                    java.util.OptionalLong.of(process.pid()), "", "not-applicable",
                    Optional.of(SandboxFailure.of(SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                            failureMessage, Set.of())));
        }
        if (!cleanup.completed()) {
            processFailure = SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                    "process-tree cleanup left survivors", Set.of());
        }
        OptionalInt exit = OptionalInt.empty();
        if (!process.isAlive()) {
            try { exit = OptionalInt.of(process.exitValue()); } catch (IllegalThreadStateException ignored) { }
        }
        if (processFailure == null && exit.isPresent() && exit.getAsInt() != 0) {
            processFailure = SandboxFailure.of(
                    SandboxFailureCode.PROCESS_CRASHED, "process exited non-zero", Set.of());
        }
        return new SandboxExecutionResult(
                exit, stdout.capture(), stderr.capture(), Optional.ofNullable(processFailure),
                new SandboxExecutionObservation(handle, working.toRealPath(),
                        Duration.between(launchedAt, Instant.now()), cleanup));
    }

    private void requireCapabilitiesMatch(EffectiveSandboxExecutionSpecification spec) {
        if (!capabilities.equals(spec.runtimeCapabilities())) {
            throw new IllegalArgumentException("launcher capability evidence differs from resolved specification");
        }
    }

    private static SandboxExecutionResult setupFailure(
            EffectiveSandboxExecutionSpecification spec, SandboxFailureCode code, String message) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(OptionalInt.empty(), new BoundedCapture(new byte[0], false),
                new BoundedCapture(new byte[0], false),
                Optional.of(SandboxFailure.of(code, message, Set.of())),
                new SandboxExecutionObservation(new SandboxExecutionHandle(-1, now),
                        spec.filesystem().workingDirectory(), Duration.ZERO,
                        new SandboxCleanupObservation(true, 0, List.of(), "")));
    }

    static SandboxCleanupObservation terminateTree(Process process) {
        return terminateTree(process, TERMINATION_GRACE);
    }

    static SandboxCleanupObservation terminateTree(Process process, Duration terminationGrace) {
        return terminateTreeWithHandles(process, terminationGrace, List.of()).observation();
    }

    static TreeTermination terminateTreeWithHandles(
            Process process,
            Duration terminationGrace,
            List<ProcessHandle> previouslyObserved) {
        LinkedHashMap<Long, ProcessHandle> observed = new LinkedHashMap<>();
        previouslyObserved.forEach(handle -> observed.put(handle.pid(), handle));
        process.descendants().forEach(handle -> observed.put(handle.pid(), handle));
        List<ProcessHandle> descendants = observed.values().stream()
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        waitUntilDead(process.toHandle(), descendants, terminationGrace);
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        waitUntilDead(process.toHandle(), descendants, terminationGrace);
        List<Long> survivors = new ArrayList<>();
        descendants.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).forEach(survivors::add);
        if (process.isAlive()) survivors.add(process.pid());
        SandboxCleanupObservation observation = new SandboxCleanupObservation(
                survivors.isEmpty(), descendants.size(), survivors,
                survivors.isEmpty() ? "" : "processes remain alive after forced termination");
        return new TreeTermination(observation, descendants);
    }

    private static void waitUntilDead(
            ProcessHandle parent, List<ProcessHandle> descendants, Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline
                && (parent.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive))) {
            try { Thread.sleep(10); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); return;
            }
        }
    }

    private static boolean closeAndJoinCaptures(
            CaptureReader stdout, Thread outThread, CaptureReader stderr, Thread errThread) {
        boolean outComplete = joinCapture(outThread, 125);
        boolean errComplete = joinCapture(errThread, 125);
        stdout.close();
        stderr.close();
        return (outComplete || joinCapture(outThread, 375))
                & (errComplete || joinCapture(errThread, 375));
    }

    private static boolean joinCapture(Thread thread, long timeoutMillis) {
        try { thread.join(timeoutMillis); } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    record TreeTermination(
            SandboxCleanupObservation observation, List<ProcessHandle> descendants) {
        TreeTermination { descendants = List.copyOf(descendants); }
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
        @Override public void run() {
            byte[] buffer = new byte[8192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = limit - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(read, remaining));
                    if (read > remaining) truncated = true;
                }
            } catch (IOException ignored) { }
        }
        BoundedCapture capture() { return new BoundedCapture(bytes.toByteArray(), truncated); }

        @Override public void close() {
            try { input.close(); } catch (IOException ignored) { }
        }
    }
}
