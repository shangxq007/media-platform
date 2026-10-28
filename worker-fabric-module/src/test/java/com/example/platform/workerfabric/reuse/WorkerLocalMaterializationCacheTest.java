package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorkerLocalMaterializationCacheTest {
    @TempDir Path root;
    ArtifactPin pin(byte[] bytes) throws Exception {
        return new ArtifactPin(new ArtifactId("source"), ContentDigest.sha256(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
    }
    @Test void cacheChecksDigestAndRecoversCorruptionWithoutReusingBadBytes() throws Exception {
        var cache = new WorkerLocalMaterializationCache(root.resolve("cache"), 64);
        byte[] bytes = "verified source".getBytes(); var pin = pin(bytes);
        var first = cache.getOrMaterialize(pin, () -> new ByteArrayInputStream(bytes));
        assertArrayEquals(bytes, Files.readAllBytes(first.materializedArtifact().path()));
        assertEquals(MaterializationDisposition.LOCAL_CACHE_HIT, cache.getOrMaterialize(pin,
                () -> { throw new AssertionError("verified cache must not read remote storage"); }).disposition());
        Files.writeString(first.materializedArtifact().path(), "corrupt");
        var recovered = cache.getOrMaterialize(pin, () -> new ByteArrayInputStream(bytes));
        assertEquals(MaterializationDisposition.CORRUPTION_RECOVERED, recovered.disposition());
        assertArrayEquals(bytes, Files.readAllBytes(recovered.materializedArtifact().path()));
    }
    @Test void symlinkedRootAncestorAndCachedFileNeverExposeOrModifyOutsideBytes() throws Exception {
        var outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("link"), outside);
        assertThrows(IllegalArgumentException.class, () -> new WorkerLocalMaterializationCache(root.resolve("link/cache"), 64));
        assertFalse(Files.exists(outside.resolve("cache")));
        byte[] bytes = "outside source".getBytes(); var pin = pin(bytes);
        var cache = new WorkerLocalMaterializationCache(root.resolve("real"), 64);
        var materialized = cache.getOrMaterialize(pin, () -> new ByteArrayInputStream(bytes)).materializedArtifact().path();
        var unrelated = Files.write(outside.resolve("unrelated"), bytes);
        Files.delete(materialized); Files.createSymbolicLink(materialized, unrelated);
        assertThrows(IOException.class, () -> cache.getOrMaterialize(pin,
                () -> { throw new AssertionError("symlink must be rejected before source read"); }));
        assertArrayEquals(bytes, Files.readAllBytes(unrelated)); assertTrue(Files.isSymbolicLink(materialized));
    }
    @Test void replacingRootAfterConstructionFailsBeforeAnyReadOrWrite() throws Exception {
        var directory = root.resolve("cache"); var cache = new WorkerLocalMaterializationCache(directory, 64);
        Files.move(directory, root.resolve("preserved")); Files.createDirectory(directory);
        assertThrows(IOException.class, () -> cache.getOrMaterialize(pin(new byte[]{1}),
                () -> { throw new AssertionError("replaced root must not obtain input"); }));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void capacityAndDigestFailureCloseInputAndLeaveNoPublishedFile() throws Exception {
        for (boolean oversized : new boolean[]{false, true}) {
            var cache = new WorkerLocalMaterializationCache(root.resolve("cache-" + oversized), 8);
            byte[] actual = oversized ? new byte[100] : "bad".getBytes(); var closed = new AtomicBoolean();
            var expected = pin("different".getBytes());
            assertThrows(ArtifactMaterializationException.class, () -> cache.getOrMaterialize(expected,
                    () -> new ByteArrayInputStream(actual) { @Override public void close() { closed.set(true); } }));
            assertTrue(closed.get()); assertEquals(0, cache.usedBytes());
            try (var paths = Files.walk(root.resolve("cache-" + oversized))) {
                assertEquals(0, paths.filter(Files::isRegularFile).count());
            }
        }
    }
}
