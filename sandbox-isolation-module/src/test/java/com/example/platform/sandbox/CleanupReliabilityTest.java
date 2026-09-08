package com.example.platform.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Deterministic counterexamples: no process launch, PID lookup, or elapsed-time state change. */
class CleanupReliabilityTest {
    @Test
    void root_completion_lag_uses_existing_grace() {
        FakeProcess root = new FakeProcess(3, List.of());
        assertFalse(root.toHandle().isAlive(), "retained root handle already reports dead");

        var result = LocalBoundedProcessLauncher.terminateTreeWithHandles(
                root, Duration.ofMillis(500), List.of()).observation();

        // Own Process becomes complete at its third liveness observation. Existing 10 ms
        // polling has ample room in the original grace; no clock or production seam is needed.
        assertAll(
                () -> assertTrue(result.completed(), result.diagnostic()),
                () -> assertTrue(result.survivors().isEmpty(), result.diagnostic()),
                () -> assertEquals(0, root.forceCalls, "completion within first grace needs no KILL"));
    }

    @Test
    void descendant_signal_exception_does_not_abort_remaining_cleanup() {
        FakeHandle denied = new FakeHandle(30, true, true);
        FakeHandle later = new FakeHandle(20, true, false);
        FakeProcess root = new FakeProcess(1, List.of(later, denied));
        LocalBoundedProcessLauncher.TreeTermination result = null;
        RuntimeException escaped = null;
        try {
            result = LocalBoundedProcessLauncher.terminateTreeWithHandles(
                    root, Duration.ofMillis(500), List.of());
        } catch (RuntimeException failure) {
            escaped = failure; // Preserve the actual exception while asserting all cleanup obligations.
        }
        var observedResult = result;
        var observedException = escaped;
        assertAll(
                () -> assertEquals(1, denied.destroyCalls, "PID-descending first target was attempted"),
                () -> assertEquals(1, later.destroyCalls, "later owned descendant must still receive TERM"),
                () -> assertEquals(1, root.destroyCalls, "own root must still receive TERM"),
                () -> assertNull(observedException, "signal failure must be returned as typed cleanup failure"),
                () -> {
                    assertNotNull(observedResult, "cleanup must return failure evidence");
                    assertFalse(observedResult.observation().completed());
                    assertEquals(SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                            observedResult.observation().failure().orElseThrow().code());
                });
    }

    private static final class FakeProcess extends Process {
        final FakeHandle handle = new FakeHandle(10, false, false);
        final List<ProcessHandle> children;
        final int completesOnObservation;
        int observations;
        int destroyCalls;
        int forceCalls;
        FakeProcess(int completesOnObservation, List<ProcessHandle> children) {
            this.completesOnObservation = completesOnObservation;
            this.children = children;
        }
        @Override public boolean isAlive() { return ++observations < completesOnObservation; }
        @Override public void destroy() { destroyCalls++; }
        @Override public Process destroyForcibly() { forceCalls++; return this; }
        @Override public ProcessHandle toHandle() { return handle; }
        @Override public long pid() { return handle.pid(); }
        @Override public Stream<ProcessHandle> descendants() { return children.stream(); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { throw new AssertionError("unbounded wait is forbidden"); }
        @Override public int exitValue() { return 0; }
    }

    private static final class FakeHandle implements ProcessHandle {
        final long id;
        final boolean deny;
        boolean alive;
        int destroyCalls;
        FakeHandle(long id, boolean alive, boolean deny) {
            this.id = id; this.alive = alive; this.deny = deny;
        }
        @Override public long pid() { return id; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean destroy() {
            destroyCalls++;
            if (deny) throw new SecurityException("synthetic denied descendant TERM");
            alive = false;
            return true;
        }
        @Override public boolean destroyForcibly() { alive = false; return true; }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public CompletableFuture<ProcessHandle> onExit() {
            throw new AssertionError("not part of original cleanup");
        }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(id, other.pid()); }
        @Override public Info info() {
            return new Info() {
                public Optional<String> command() { return Optional.empty(); }
                public Optional<String> commandLine() { return Optional.empty(); }
                public Optional<String[]> arguments() { return Optional.empty(); }
                public Optional<Instant> startInstant() { return Optional.of(Instant.EPOCH); }
                public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
                public Optional<String> user() { return Optional.empty(); }
            };
        }
    }
}
