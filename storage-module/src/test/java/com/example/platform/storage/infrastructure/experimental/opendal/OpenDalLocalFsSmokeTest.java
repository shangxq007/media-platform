package com.example.platform.storage.infrastructure.experimental.opendal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OpenDAL local filesystem smoke test.
 * Tests basic write/read/stat operations using Java NIO (POC only).
 */
class OpenDalLocalFsSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void testLocalFsWriteReadStat() throws IOException {
        // Given
        OpenDalExperimentalProperties props = new OpenDalExperimentalProperties();
        props.setEnabled(true);
        props.setBackend("fs");
        props.setRoot(tempDir.toString());
        props.setMode("poc");

        OpenDalMaterializer materializer = new OpenDalMaterializer(props);

        String objectKey = "test/hello-opendal.txt";
        byte[] data = "Hello OpenDAL POC!".getBytes();

        // When - write
        boolean written = materializer.write(objectKey, data);
        assertTrue(written, "Write should succeed");

        // When - exists
        boolean exists = materializer.exists(objectKey);
        assertTrue(exists, "Object should exist after write");

        // When - read
        Optional<byte[]> readData = materializer.read(objectKey);
        assertTrue(readData.isPresent(), "Read should return data");
        assertArrayEquals(data, readData.get(), "Read data should match written data");

        // When - size
        Optional<Long> size = materializer.size(objectKey);
        assertTrue(size.isPresent(), "Size should be available");
        assertEquals(data.length, size.get(), "Size should match data length");

        // When - read non-existent
        Optional<byte[]> missing = materializer.read("non-existent-key");
        assertFalse(missing.isPresent(), "Read of non-existent key should return empty");

        // When - exists non-existent
        boolean missingExists = materializer.exists("non-existent-key");
        assertFalse(missingExists, "Non-existent key should not exist");
    }
}
