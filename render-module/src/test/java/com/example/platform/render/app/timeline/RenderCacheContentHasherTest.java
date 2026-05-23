package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderCacheContentHasherTest {

    @TempDir
    Path tempDir;

    @Test
    void hashFileIsStable() throws Exception {
        Path file = tempDir.resolve("a.bin");
        Files.write(file, new byte[] {1, 2, 3});
        String h1 = RenderCacheContentHasher.hashFile(file);
        String h2 = RenderCacheContentHasher.hashFile(file);
        assertTrue(h1.startsWith("sha256:"));
        assertEquals(h1, h2);
    }
}
