package com.example.platform.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Exercises production cleanup/aggregation using owned doubles only; never launches a process. */
class CleanupFailurePropagationTest {
    private static final Duration NO_WAIT = Duration.ZERO;

    @Test void root_remains_live() {
        Root root = new Root(List.of()); root.stubborn = true;
        var result = terminate(root, List.of());
        failed(result.observation());
        assertEquals(List.of(10L), result.observation().survivors());
        assertEquals(1, root.terms); assertEquals(1, root.forces);
    }

    @Test void descendant_remains_live() {
        Handle child = new Handle(20); child.stubborn = true;
        var result = terminate(new Root(List.of(child)), List.of());
        failed(result.observation());
        assertEquals(List.of(20L), result.observation().survivors());
    }

    @Test void failed_signal_with_empty_survivors_stays_failed() {
        Handle child = new Handle(30); child.denyTerm = true;
        Handle later = new Handle(20); Root root = new Root(List.of(child, later));
        var result = terminate(root, List.of());
        failed(result.observation()); assertTrue(result.observation().survivors().isEmpty());
        assertEquals(1, later.terms); assertEquals(1, root.terms);
        assertTrue(result.observation().failureMessage().contains("SecurityException"));
    }

    @Test void rejected_signal_while_live_stays_failed_after_force() {
        Handle child = new Handle(20); child.rejectTerm = true;
        var result = terminate(new Root(List.of(child)), List.of());
        failed(result.observation()); assertTrue(result.observation().survivors().isEmpty());
    }

    @Test void rejected_signal_after_exit_is_not_a_failure() {
        Handle child = new Handle(20); child.alive = false; child.rejectTerm = true;
        assertTrue(terminate(new Root(List.of(child)), List.of()).observation().completed());
    }

    @Test void failed_observation_does_not_abort_root_or_other_owned_objects() {
        Handle child = new Handle(30); child.denyAlive = true;
        Handle later = new Handle(20); Root root = new Root(List.of(child, later));
        var result = terminate(root, List.of());
        failed(result.observation()); assertEquals(List.of(30L), result.observation().survivors());
        assertEquals(1, later.terms); assertEquals(1, root.terms); assertEquals(1, child.forces);
    }

    @Test void failed_enumeration_still_cleans_retained_handles_and_root() {
        Handle retained = new Handle(20); Root root = new Root(List.of()); root.denyEnumeration = true;
        var result = terminate(root, List.of(retained));
        failed(result.observation()); assertTrue(result.observation().survivors().isEmpty());
        assertSame(retained, result.descendants().getFirst());
        assertEquals(1, retained.terms); assertEquals(1, root.terms);
    }

    @Test void partial_enumeration_preserves_already_observed_handles() {
        Handle retained = new Handle(20); Handle fresh = new Handle(30);
        Root root = new Root(List.of(fresh)); root.partialEnumeration = true;
        var result = terminate(root, List.of(retained));
        failed(result.observation()); assertEquals(2, result.descendants().size());
        assertEquals(1, retained.terms); assertEquals(1, fresh.terms); assertEquals(1, root.terms);
    }

    @Test void unknown_retained_identity_fails_closed() {
        Handle retained = new Handle(20); retained.start = Optional.empty();
        var result = terminate(new Root(List.of()), List.of(retained));
        failed(result.observation()); assertSame(retained, result.descendants().getFirst());
    }

    @Test void exited_retained_handle_does_not_require_live_process_info() {
        Handle retained = new Handle(20); retained.alive = false; retained.start = Optional.empty();
        var result = terminate(new Root(List.of()), List.of(retained));
        assertTrue(result.observation().completed(), result.observation().diagnostic());
        assertSame(retained, result.descendants().getFirst());
    }

    @Test void failed_retained_identity_observation_still_cleans_other_objects() {
        Handle retained = new Handle(30); retained.denyInfo = true;
        Handle later = new Handle(20); Root root = new Root(List.of());
        var result = terminate(root, List.of(retained, later));
        failed(result.observation()); assertEquals(1, later.terms); assertEquals(1, root.terms);
    }

    @Test void failed_force_does_not_abort_later_force_or_root() {
        Handle denied = new Handle(30); denied.stubborn = true; denied.denyForce = true;
        Handle later = new Handle(20); later.exitOnForce = true;
        Root root = new Root(List.of(denied, later)); root.exitOnForce = true;
        var result = terminate(root, List.of()); failed(result.observation());
        assertEquals(List.of(30L), result.observation().survivors());
        assertEquals(1, later.forces); assertEquals(1, root.forces);
    }

    @Test void transient_observation_error_is_retained_after_confirmed_exit() {
        Handle child = new Handle(20); child.failOneObservation = true;
        var result = terminate(new Root(List.of(child)), List.of());
        failed(result.observation()); assertTrue(result.observation().survivors().isEmpty());
    }

    @Test void conflicting_same_pid_never_replaces_retained_object() {
        Handle retained = new Handle(20); Handle replacement = new Handle(20);
        replacement.start = Optional.of(Instant.EPOCH.plusSeconds(1));
        var result = terminate(new Root(List.of(replacement)), List.of(retained));
        failed(result.observation()); assertSame(retained, result.descendants().getFirst());
        assertEquals(1, retained.terms); assertEquals(0, replacement.terms);
    }

    @Test void matching_same_pid_keeps_retained_object() {
        Handle retained = new Handle(20); Handle sameIdentity = new Handle(20);
        var result = terminate(new Root(List.of(sameIdentity)), List.of(retained));
        assertTrue(result.observation().completed());
        assertSame(retained, result.descendants().getFirst()); assertEquals(0, sameIdentity.terms);
    }

    @Test void interrupted_wait_preserves_failure_and_continues_force() {
        Root root = new Root(List.of()); root.exitOnForce = true;
        Thread.currentThread().interrupt();
        try {
            var result = LocalBoundedProcessLauncher.terminateTreeWithHandles(
                    root, Duration.ofMillis(50), List.of());
            failed(result.observation()); assertTrue(result.observation().survivors().isEmpty());
            assertEquals(1, root.forces); assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test void root_signal_exception_does_not_prevent_descendant_force() {
        Handle child = new Handle(20); child.rejectTerm = true;
        Root root = new Root(List.of(child)); root.denyTerm = true;
        var result = terminate(root, List.of()); failed(result.observation());
        assertEquals(1, child.forces); assertEquals(1, root.forces);
    }

    @Test void root_observation_uncertainty_is_a_blocking_survivor() {
        Root root = new Root(List.of()); root.denyAlive = true;
        var result = terminate(root, List.of()); failed(result.observation());
        assertEquals(List.of(10L), result.observation().survivors());
    }

    @Test void bubblewrap_aggregator_preserves_error_with_empty_survivors() throws Exception {
        var local = SandboxCleanupObservation.failed(0, List.of(), "synthetic observation denied");
        Method method = BubblewrapSandboxProcessLauncher.class.getDeclaredMethod(
                "cleanupObservation", long.class, SandboxCleanupObservation.class, boolean.class);
        method.setAccessible(true);
        var combined = (SandboxCleanupObservation) method.invoke(null, 10L, local, true);
        failed(combined); assertTrue(combined.survivors().isEmpty());
        assertTrue(combined.failureMessage().contains(local.failureMessage()));
    }

    @Test void container_aggregator_preserves_error_with_empty_survivors() {
        var local = SandboxCleanupObservation.failed(0, List.of(), "synthetic observation denied");
        var combined = ContainerSandboxProcessLauncher.cleanupObservation(
                "synthetic", 10L, removed(), local, true);
        failed(combined); assertTrue(combined.survivors().isEmpty());
        assertTrue(combined.failureMessage().contains(local.failureMessage()));
    }

    @Test void local_primary_and_cleanup_failures_remain_separate() throws Exception {
        var cleanup = SandboxCleanupObservation.failed(0, List.of(), "synthetic denied");
        Method method = LocalBoundedProcessLauncher.class.getDeclaredMethod(
                "selectResultFailure", SandboxFailure.class, SandboxCleanupObservation.class);
        method.setAccessible(true);
        for (var code : List.of(SandboxFailureCode.PROCESS_TIMEOUT,
                SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT, SandboxFailureCode.SANDBOX_RUNTIME_LOST)) {
            var primary = SandboxFailure.of(code, "primary", Set.of());
            assertEquals(Optional.of(primary), method.invoke(null, primary, cleanup));
            failed(cleanup);
        }
        assertEquals(cleanup.failure(), method.invoke(null, null, cleanup));
        assertEquals(Optional.empty(), method.invoke(null, null, SandboxCleanupObservation.succeeded(0, List.of())));
    }

    @Test void container_preserves_preliminary_error_after_successful_final_cleanup() {
        Handle child = new Handle(20); child.denyTerm = true;
        Root root = new Root(List.of(child));
        var preliminary = terminate(root, List.of());
        child.denyTerm = false;
        var last = terminate(root, preliminary.descendants());
        assertTrue(last.observation().completed());
        var combined = ContainerSandboxProcessLauncher.cleanupObservation(
                "synthetic", 10L, removed(), preliminary, last, true);
        failed(combined); assertTrue(combined.survivors().isEmpty());
        assertTrue(combined.failureMessage().contains("SecurityException"));
    }

    @Test void container_final_error_with_live_helper_is_not_detached_success() {
        Root root = new Root(List.of());
        var preliminary = terminate(root, List.of());
        Handle child = new Handle(20); child.stubborn = true; child.denyTerm = true;
        var last = terminate(new Root(List.of(child)), List.of());
        var combined = ContainerSandboxProcessLauncher.cleanupObservation(
                "synthetic", 10L, removed(), preliminary, last, true);
        failed(combined); assertEquals(List.of(20L), combined.survivors());
        assertTrue(combined.detachedEngineHelpers().isEmpty());
        assertTrue(combined.failureMessage().contains("SecurityException"));
    }

    @Test void container_preliminary_live_survivor_can_resolve_without_sticky_error() {
        Handle child = new Handle(20); child.stubborn = true;
        Root root = new Root(List.of(child));
        var preliminary = terminate(root, List.of());
        assertFalse(preliminary.observation().completed());
        child.stubborn = false;
        var last = terminate(root, preliminary.descendants());
        assertTrue(ContainerSandboxProcessLauncher.cleanupObservation(
                "synthetic", 10L, removed(), preliminary, last, true).completed());
    }

    @Test void container_two_pass_known_live_helper_contract_is_preserved() {
        Handle child = new Handle(20); child.stubborn = true;
        Root root = new Root(List.of(child));
        var preliminary = terminate(root, List.of());
        var last = terminate(root, preliminary.descendants());
        var combined = ContainerSandboxProcessLauncher.cleanupObservation(
                "synthetic", 10L, removed(), preliminary, last, true);
        assertTrue(combined.completed()); assertEquals(List.of(20L), combined.detachedEngineHelpers());
        assertTrue(combined.survivors().isEmpty());
    }

    private static LocalBoundedProcessLauncher.TreeTermination terminate(Root root, List<ProcessHandle> retained) {
        return LocalBoundedProcessLauncher.terminateTreeWithHandles(root, NO_WAIT, retained);
    }
    private static void failed(SandboxCleanupObservation result) {
        assertFalse(result.completed(), result.diagnostic());
        assertEquals(SandboxFailureCode.SANDBOX_CLEANUP_FAILED, result.failure().orElseThrow().code());
    }
    private static ContainerSandboxProcessLauncher.ContainerRemovalObservation removed() {
        return new ContainerSandboxProcessLauncher.ContainerRemovalObservation(true, true, 1, "removed", "");
    }

    private static final class Root extends Process {
        final List<ProcessHandle> children;
        boolean alive = true, stubborn, exitOnForce, denyTerm, denyAlive, denyEnumeration, partialEnumeration;
        int terms, forces;
        Root(List<ProcessHandle> children) { this.children = children; }
        @Override public long pid() { return 10; }
        @Override public boolean isAlive() {
            if (denyAlive) throw new SecurityException("synthetic root observation");
            return alive;
        }
        @Override public void destroy() {
            terms++; if (denyTerm) throw new SecurityException("synthetic root TERM");
            if (!stubborn && !exitOnForce) alive = false;
        }
        @Override public Process destroyForcibly() { forces++; if (!stubborn) alive = false; return this; }
        @Override public ProcessHandle toHandle() { Handle handle = new Handle(10); handle.alive = alive; return handle; }
        @Override public Stream<ProcessHandle> descendants() {
            if (denyEnumeration) throw new SecurityException("synthetic enumeration");
            if (partialEnumeration) return Stream.concat(children.stream(), Stream.generate(() -> {
                throw new IllegalStateException("synthetic partial enumeration");
            }));
            return children.stream();
        }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { throw new AssertionError("unbounded wait"); }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
    }
    private static final class Handle implements ProcessHandle {
        final long id;
        boolean alive = true, stubborn, denyTerm, rejectTerm, denyAlive, denyInfo, denyForce, exitOnForce, failOneObservation;
        Optional<Instant> start = Optional.of(Instant.EPOCH);
        int terms, forces;
        Handle(long id) { this.id = id; }
        @Override public long pid() { return id; }
        @Override public boolean isAlive() {
            if (failOneObservation) { failOneObservation = false; throw new SecurityException("synthetic transient observation"); }
            if (denyAlive) throw new SecurityException("synthetic observation"); return alive;
        }
        @Override public boolean destroy() {
            terms++; if (denyTerm) throw new SecurityException("synthetic TERM");
            if (rejectTerm) return false;
            if (!stubborn && !exitOnForce) alive = false; return true;
        }
        @Override public boolean destroyForcibly() {
            forces++; if (denyForce) throw new SecurityException("synthetic KILL");
            if (!stubborn) alive = false; return true;
        }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public CompletableFuture<ProcessHandle> onExit() { throw new AssertionError("not used"); }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(id, other.pid()); }
        @Override public Info info() {
            if (denyInfo) throw new SecurityException("synthetic identity observation");
            return new Info() {
                public Optional<String> command() { return Optional.empty(); }
                public Optional<String> commandLine() { return Optional.empty(); }
                public Optional<String[]> arguments() { return Optional.empty(); }
                public Optional<Instant> startInstant() { return start; }
                public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
                public Optional<String> user() { return Optional.empty(); }
            };
        }
    }
}
