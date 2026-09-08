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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
        OptionalInt exit = OptionalInt.empty();
        if (!process.isAlive()) {
            try { exit = OptionalInt.of(process.exitValue()); } catch (IllegalThreadStateException ignored) { }
        }
        if (processFailure == null && exit.isPresent() && exit.getAsInt() != 0) {
            processFailure = SandboxFailure.of(
                    SandboxFailureCode.PROCESS_CRASHED, "process exited non-zero", Set.of());
        }
        return new SandboxExecutionResult(
                exit, stdout.capture(), stderr.capture(), selectResultFailure(processFailure, cleanup),
                new SandboxExecutionObservation(handle, working.toRealPath(),
                        Duration.between(launchedAt, Instant.now()), cleanup));
    }

    static Optional<SandboxFailure> selectResultFailure(
            SandboxFailure primaryFailure, SandboxCleanupObservation cleanup) {
        return primaryFailure == null ? cleanup.failure() : Optional.of(primaryFailure);
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
        CleanupAttempts attempts = new CleanupAttempts();
        LinkedHashMap<Long, ProcessHandle> observed = new LinkedHashMap<>();
        for (ProcessHandle handle : previouslyObserved) retainHandle(observed, handle, true, attempts);
        try (var fresh = process.descendants()) {
            fresh.forEach(handle -> retainHandle(observed, handle, false, attempts));
        } catch (RuntimeException failure) {
            attempts.failed("descendant enumeration", failure);
        }
        List<ProcessHandle> descendants = observed.values().stream()
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).toList();
        for (ProcessHandle handle : descendants) signal(handle, false, attempts);
        attempts.run("root TERM", process::destroy);
        waitUntilDead(process, descendants, terminationGrace, attempts);
        for (ProcessHandle handle : descendants) {
            if (attempts.alive("descendant " + handle.pid(), handle::isAlive)) {
                signal(handle, true, attempts);
            }
        }
        if (attempts.alive("root", process::isAlive)) {
            attempts.run("root KILL", process::destroyForcibly);
        }
        waitUntilDead(process, descendants, terminationGrace, attempts);
        List<Long> survivors = new ArrayList<>();
        for (ProcessHandle handle : descendants) {
            if (attempts.alive("descendant " + handle.pid(), handle::isAlive)) survivors.add(handle.pid());
        }
        if (attempts.alive("root", process::isAlive)) survivors.add(process.pid());
        List<String> failures = new ArrayList<>(attempts.failures);
        if (!survivors.isEmpty()) failures.add("processes remain alive after forced termination");
        SandboxCleanupObservation observation = new SandboxCleanupObservation(
                failures.isEmpty(), descendants.size(), survivors, String.join("; ", failures));
        return new TreeTermination(observation, descendants, List.copyOf(attempts.failures));
    }

    private static void retainHandle(
            LinkedHashMap<Long, ProcessHandle> observed, ProcessHandle handle,
            boolean retained, CleanupAttempts attempts) {
        try {
            long pid = handle.pid();
            ProcessHandle existing = observed.putIfAbsent(pid, handle);
            // Exited retained handles may no longer have live-process Info. Their own
            // liveness observation still refers to the retained identity, never a PID lookup.
            if (retained && handle.isAlive() && handle.info().startInstant().isEmpty()) {
                attempts.failures.add("retained descendant " + pid + " identity unavailable");
            }
            if (existing != null && existing != handle) {
                Optional<Instant> original = existing.info().startInstant();
                Optional<Instant> fresh = handle.info().startInstant();
                if (original.isEmpty() || fresh.isEmpty() || !original.equals(fresh)) {
                    attempts.failures.add("descendant " + pid + " identity conflict or unavailable");
                }
                // Keep the owned object even when a fresh enumeration returns the same PID.
            }
        } catch (RuntimeException failure) {
            attempts.failed("descendant identity", failure);
        }
    }

    private static void signal(ProcessHandle handle, boolean force, CleanupAttempts attempts) {
        String target = "descendant " + handle.pid();
        attempts.run(target + (force ? " KILL" : " TERM"), () -> {
            boolean accepted = force ? handle.destroyForcibly() : handle.destroy();
            // A false return can also mean exit raced with the request. Only confirmed exit
            // resolves that case; an exception or a still-live/unknown target remains a failure.
            if (!accepted && attempts.alive(target, handle::isAlive)) {
                attempts.failures.add(target + (force ? " KILL" : " TERM") + " rejected while live or unknown");
            }
        });
    }

    private static void waitUntilDead(
            Process parent, List<ProcessHandle> descendants, Duration duration, CleanupAttempts attempts) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            boolean alive = attempts.alive("root", parent::isAlive);
            for (ProcessHandle handle : descendants) {
                alive |= attempts.alive("descendant " + handle.pid(), handle::isAlive);
            }
            if (!alive) return;
            try { Thread.sleep(10); } catch (InterruptedException interrupted) {
                attempts.failures.add("process cleanup wait interrupted");
                Thread.currentThread().interrupt(); return;
            }
        }
    }

    /** Operation uncertainty is sticky, separate from an acceptance-time survivor snapshot. */
    private static final class CleanupAttempts {
        private final Set<String> failures = new LinkedHashSet<>();

        private void failed(String operation, RuntimeException failure) {
            failures.add(operation + " failed: " + failure.getClass().getSimpleName());
        }

        private void run(String operation, Runnable action) {
            try { action.run(); } catch (RuntimeException failure) { failed(operation, failure); }
        }

        private boolean alive(String target, BooleanSupplier observation) {
            try { return observation.getAsBoolean(); } catch (RuntimeException failure) {
                failed(target + " observation", failure);
                return true;
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
            SandboxCleanupObservation observation, List<ProcessHandle> descendants,
            List<String> operationFailures) {
        TreeTermination {
            descendants = List.copyOf(descendants);
            operationFailures = List.copyOf(operationFailures);
        }
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
