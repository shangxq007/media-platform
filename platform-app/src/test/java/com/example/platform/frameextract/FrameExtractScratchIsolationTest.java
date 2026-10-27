package com.example.platform.frameextract;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.contract.media.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises production scratch creation/read/cleanup, not a Boolean model of isolation. */
class FrameExtractScratchIsolationTest {
    @TempDir Path root;

    @Test void differentProjectsWithTheSameKeyCannotOverwriteOrDeleteEachOther() throws Exception {
        concurrent("other-project");
    }
    @Test void overlappingRetriesOfTheSameRequestOwnDifferentScratch() throws Exception {
        concurrent("project");
    }
    private void concurrent(String secondProject) throws Exception {
        var ready = new CountDownLatch(2);
        var releaseSlow = new CountDownLatch(1);
        var paths = new ConcurrentHashMap<String, Path>();
        var provider = new FrameExtractProvider() {
            public FrameExtractManifest manifest() { return new FfmpegCpuProvider("unused", "unused").manifest(); }
            public FrameExtractResult render(String capability, Path input, Path work, String format,
                    Integer width, Integer quality, double instant, BooleanSupplier cancelled) {
                try {
                    String content = Files.readString(input);
                    paths.put(content, work);
                    ready.countDown();
                    assertTrue(ready.await(5, TimeUnit.SECONDS));
                    if (content.equals("slow")) {
                        assertTrue(releaseSlow.await(5, TimeUnit.SECONDS));
                        assertFalse(Files.exists(paths.get("fast")), "fast invocation should have cleaned only itself");
                    }
                    assertEquals(content, Files.readString(input), "concurrent source must remain unchanged");
                    return FrameExtractResult.failure("CONTROLLED_COMPLETION");
                } catch (Exception e) { throw new AssertionError(e); }
            }
        };
        var adapter = FrameExtractExecutionAdapter.of(provider, root);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var slow = executor.submit(() -> adapter.invoke(request("project", "same-key"), "slow".getBytes(), () -> false));
            var fast = executor.submit(() -> adapter.invoke(request(secondProject, "same-key"), "fast".getBytes(), () -> false));
            assertEquals("CONTROLLED_COMPLETION", fast.get(8, TimeUnit.SECONDS).failureCode());
            releaseSlow.countDown();
            assertEquals("CONTROLLED_COMPLETION", slow.get(8, TimeUnit.SECONDS).failureCode());
            assertNotEquals(paths.get("slow"), paths.get("fast"));
            try (var remaining = Files.list(root)) { assertEquals(0, remaining.count()); }
        } finally {
            releaseSlow.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
        }
    }
    @Test void requestKeyCannotSelectAPathAndSymlinkRootsFailBeforeProviderExecution() throws Exception {
        Path scratch = Files.createDirectory(root.resolve("scratch"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path marker = Files.writeString(outside.resolve("keep"), "unchanged");
        var invoked = new java.util.concurrent.atomic.AtomicInteger();
        var provider = new FrameExtractProvider() {
            public FrameExtractManifest manifest() { return new FfmpegCpuProvider("unused", "unused").manifest(); }
            public FrameExtractResult render(String capability, Path input, Path work, String format,
                    Integer width, Integer quality, double instant, BooleanSupplier cancelled) {
                invoked.incrementAndGet();
                assertEquals(scratch, work.getParent());
                assertEquals("bytes", assertDoesNotThrow(() -> Files.readString(input)));
                return FrameExtractResult.failure("CONTROLLED_COMPLETION");
            }
        };
        var key = "../../outside/keep";
        assertEquals("CONTROLLED_COMPLETION", FrameExtractExecutionAdapter.of(provider, scratch)
                .invoke(request("project", key), "bytes".getBytes(), () -> false).failureCode());
        Path link = Files.createSymbolicLink(root.resolve("link"), outside);
        assertEquals("PROVIDER_IO_FAILED", FrameExtractExecutionAdapter.of(provider, link)
                .invoke(request("project", key), "bytes".getBytes(), () -> false).failureCode());
        assertEquals(1, invoked.get());
        assertEquals("unchanged", Files.readString(marker));
    }
    private static ThumbnailContracts.Request request(String project, String key) {
        return new ThumbnailContracts.Request("tenant", project, "asset", .25, "jpeg", 160, 80, key);
    }
}
