package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalFsStorageProviderTest {

    private LocalFsStorageProvider provider;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("storage-test");
        provider = new LocalFsStorageProvider(tempDir.toString());
    }

    @Test
    void codeReturnsLocalFsStorageProvider() {
        assertEquals("localFsStorageProvider", provider.code());
    }

    @Test
    void putStoresFileOnDisk() throws Exception {
        PutObjectCommand command = new PutObjectCommand(
                "my-bucket", "path/to/file.txt", "hello world".getBytes(), "text/plain");

        StorageObjectRef ref = provider.put(command);

        assertNotNull(ref);
        assertEquals("localFsStorageProvider", ref.provider());
        assertEquals("my-bucket", ref.bucket());
        assertEquals("path/to/file.txt", ref.objectKey());

        Path stored = tempDir.resolve("my-bucket").resolve("path/to/file.txt");
        assertTrue(Files.exists(stored), "File should exist on disk");
        assertEquals("hello world", Files.readString(stored));
    }

    @Test
    void putCreatesDirectories() throws Exception {
        PutObjectCommand command = new PutObjectCommand(
                "bucket", "deep/nested/path/data.bin", new byte[]{1, 2, 3}, "application/octet-stream");

        provider.put(command);

        Path stored = tempDir.resolve("bucket").resolve("deep/nested/path/data.bin");
        assertTrue(Files.exists(stored));
    }

    @Test
    void putOverwritesExistingFile() throws Exception {
        PutObjectCommand command1 = new PutObjectCommand(
                "b", "f.txt", "version1".getBytes(), "text/plain");
        PutObjectCommand command2 = new PutObjectCommand(
                "b", "f.txt", "version2".getBytes(), "text/plain");

        provider.put(command1);
        provider.put(command2);

        Path stored = tempDir.resolve("b").resolve("f.txt");
        assertEquals("version2", Files.readString(stored));
    }

    @Test
    void presignReturnsFileUri() {
        String uri = provider.presign("some/file.txt");
        assertNotNull(uri);
        assertTrue(uri.startsWith("file:/"), "presign should return a file URI");
        assertTrue(uri.endsWith("some/file.txt"), "presign URI should contain the object key");
    }

    @Test
    void putWithEmptyContent() throws Exception {
        PutObjectCommand command = new PutObjectCommand(
                "b", "empty.txt", new byte[0], "text/plain");

        StorageObjectRef ref = provider.put(command);

        assertNotNull(ref);
        Path stored = tempDir.resolve("b").resolve("empty.txt");
        assertTrue(Files.exists(stored));
        assertEquals(0, Files.size(stored));
    }

    @Test
    void listObjectsReturnsStoredFiles() {
        provider.put(new PutObjectCommand("artifacts", "a/out.mp4", new byte[]{1}, "video/mp4"));
        provider.put(new PutObjectCommand("artifacts", "b/out.mp4", new byte[]{2}, "video/mp4"));
        assertEquals(2, provider.listObjects("artifacts", "", 10).size());
    }
}
