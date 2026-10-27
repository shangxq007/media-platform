package com.example.platform.frameextract;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.contract.media.*;
import com.example.platform.sandbox.execution.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fault injection into the real provider/adapter with a real, bounded, still-alive child.
 * This proves retention on uncertainty, not codec correctness or OS deployment isolation. */
class FrameExtractUnknownCleanupTest {
    @TempDir Path root;
    @Test void uncertainProbeRetainsInputEvenWhenCancellationWasRequested() throws Exception {
        scenario("probe", true, false, false);
    }
    @Test void uncertainRenderRetainsInputEvenWhenCancellationWasRequested() throws Exception {
        scenario("render", true, false, false);
    }
    @Test void unclassifiedProviderExceptionRetainsItsStartedChildInput() throws Exception {
        scenario("render", false, true, false);
    }
    @Test void provenExitedNonzeroChildAllowsOnlyItsOwnScratchCleanup() throws Exception {
        scenario("render", false, false, true);
    }
    private void scenario(String stage, boolean cancel, boolean throwing, boolean exited) throws Exception {
        var requested = new AtomicBoolean();
        Path marker = root.resolve("started");
        Path unrelated = Files.writeString(root.resolve("not-owned"), "keep");
        Process[] child = {null};
        Path[] work = {null};
        Instant[] birth = {null};
        var backend = new CancellableExecutionBackend() {
            public String backendId() { return "fault-injected-observation"; }
            public boolean supports(TaskCapability c) { return c == TaskCapability.THUMBNAIL; }
            public ExecutionResult execute(ExecutionRequest q, java.util.function.BooleanSupplier c) {
                boolean probe = q.arguments().contains("-show_entries");
                if (probe && !stage.equals("probe")) return ExecutionResult.success(0, "1.0", "", 1);
                try {
                    work[0] = Path.of(q.workingDirectory());
                    var builder = new ProcessBuilder(Path.of("/usr/bin/python3").toRealPath().toString(), "-c",
                            "from pathlib import Path; import time,sys; "
                            + "Path(sys.argv[2]).write_bytes(Path(sys.argv[1]).read_bytes()); "
                            + (exited ? "time.sleep(.2); sys.exit(7)" : "time.sleep(20)"),
                            q.payload().get("input"), marker.toString());
                    builder.environment().clear();
                    builder.environment().put("PATH", "/usr/bin:/bin");
                    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    builder.redirectError(root.resolve("child.stderr").toFile());
                    child[0] = builder.start();
                    birth[0] = child[0].info().startInstant().orElse(null);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    while (!Files.exists(marker) && System.nanoTime() < deadline && child[0].isAlive())
                        Thread.sleep(10);
                    assertTrue(Files.exists(marker), "actual child must consume this invocation's input");
                    assertEquals("owned-media-bytes", Files.readString(marker));
                    if (exited) {
                        assertTrue(child[0].waitFor(3, TimeUnit.SECONDS));
                        assertEquals(7, child[0].exitValue());
                        return ExecutionResult.failure(7, "", 1, "PROCESS_CRASHED", "known exit");
                    }
                    assertTrue(child[0].isAlive());
                    assertNotNull(birth[0]);
                    requested.set(cancel);
                    if (throwing) throw new IllegalStateException("unclassified observation failure");
                    return ExecutionResult.failure(-1, "", 1, "SANDBOX_CLEANUP_FAILED", "unknown cleanup");
                } catch (java.io.IOException | InterruptedException e) { throw new AssertionError(e); }
            }
        };
        var registry = new ExecutionBackendRegistry() {
            public Optional<ExecutionBackend> resolve(TaskCapability c) { return Optional.of(backend); }
            public int size() { return 1; }
        };
        var provider = new FfmpegCpuProvider(registry, "/usr/bin/ffmpeg", "/usr/bin/ffprobe");
        var adapter = FrameExtractExecutionAdapter.of(provider, root.resolve("scratch"));
        var request = new ThumbnailContracts.Request("tenant", "project", "source", .25, "jpeg", 160, 80, "key");
        try {
            if (throwing) {
                assertThrows(IllegalStateException.class,
                        () -> adapter.invoke(request, "owned-media-bytes".getBytes(), requested::get));
            } else {
                var result = adapter.invoke(request, "owned-media-bytes".getBytes(), requested::get);
                assertFalse(result.succeeded());
                assertEquals(exited ? "PROCESS_CRASHED" : "SANDBOX_CLEANUP_FAILED", result.failureCode());
            }
            if (exited) assertFalse(Files.exists(work[0]));
            else {
                assertTrue(child[0].isAlive());
                assertEquals(birth[0], child[0].info().startInstant().orElseThrow());
                assertEquals("owned-media-bytes", Files.readString(work[0].resolve("input")));
                assertFalse(Thread.currentThread().isInterrupted(), "uncertain cleanup must not become cancellation");
            }
            assertEquals("keep", Files.readString(unrelated));
        } finally {
            // Only this test's exact Process is stopped. Retained scratch is removed by @TempDir
            // only after this termination proof, never while the fault-injected child survives.
            if (child[0] != null) {
                if (child[0].isAlive()) child[0].destroyForcibly();
                assertTrue(child[0].waitFor(5, TimeUnit.SECONDS));
                assertFalse(child[0].isAlive());
            }
        }
    }
}
