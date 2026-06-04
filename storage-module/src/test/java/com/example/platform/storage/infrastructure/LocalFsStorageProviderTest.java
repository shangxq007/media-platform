package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
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

    @Test
    void putWithDeepNestedLegalPath() {
        PutObjectCommand command = new PutObjectCommand(
                "media", "tenant/t1/workspace/w1/project/p1/assets/a1/source/video.mp4",
                "content".getBytes(), "video/mp4");

        StorageObjectRef ref = provider.put(command);

        assertNotNull(ref);
        assertEquals("media", ref.bucket());
        Path stored = tempDir.resolve("media").resolve("tenant/t1/workspace/w1/project/p1/assets/a1/source/video.mp4");
        assertTrue(Files.exists(stored));
    }

    @Test
    void getReturnsContent() {
        provider.put(new PutObjectCommand("b", "dir/file.txt", "hello".getBytes(), "text/plain"));

        Optional<byte[]> content = provider.get("b", "dir/file.txt");

        assertNotNull(content);
        assertTrue(content.isPresent());
        assertEquals("hello", new String(content.get()));
    }

    @Test
    void getReturnsEmptyForNonExistent() {
        Optional<byte[]> content = provider.get("b", "nonexistent.txt");
        assertNotNull(content);
        assertTrue(content.isEmpty());
    }

    @Test
    void deleteRemovesFile() {
        provider.put(new PutObjectCommand("b", "to-delete.txt", "bye".getBytes(), "text/plain"));

        boolean result = provider.delete("b", "to-delete.txt");

        assertTrue(result);
        assertTrue(!Files.exists(tempDir.resolve("b").resolve("to-delete.txt")));
    }

    @Test
    void deleteNonExistentReturnsTrue() {
        boolean result = provider.delete("b", "nonexistent.txt");
        assertTrue(result);
    }

    @Test
    void presignWithBucketAndKey() {
        String uri = provider.presign("my-bucket", "path/to/file.txt");
        assertNotNull(uri);
        assertTrue(uri.contains("my-bucket"));
        assertTrue(uri.contains("path/to/file.txt"));
    }

    @Test
    void saveArtifactAndGetArtifact() {
        provider.saveArtifact("art-001", "artifact-content".getBytes(), Map.of("key", "value"));

        byte[] content = provider.getArtifact("art-001");
        assertEquals("artifact-content", new String(content));

        Map<String, Object> metadata = provider.getArtifactMetadata("art-001");
        assertEquals("value", metadata.get("key"));
    }

    @Test
    void listObjectsWithPrefix() {
        provider.put(new PutObjectCommand("b", "prefix/a.txt", new byte[]{1}, "text/plain"));
        provider.put(new PutObjectCommand("b", "prefix/b.txt", new byte[]{2}, "text/plain"));
        provider.put(new PutObjectCommand("b", "other/c.txt", new byte[]{3}, "text/plain"));

        assertEquals(2, provider.listObjects("b", "prefix/", 10).size());
    }

    @Test
    void listObjectsRespectsMaxKeys() {
        provider.put(new PutObjectCommand("b", "f1.txt", new byte[]{1}, "text/plain"));
        provider.put(new PutObjectCommand("b", "f2.txt", new byte[]{2}, "text/plain"));
        provider.put(new PutObjectCommand("b", "f3.txt", new byte[]{3}, "text/plain"));

        assertEquals(2, provider.listObjects("b", "", 2).size());
    }

    @Test
    void listObjectsNonExistentBucketReturnsEmpty() {
        assertEquals(0, provider.listObjects("nonexistent", "", 10).size());
    }

    @Test
    void getArtifactMetadataEmptyForUnknown() {
        Map<String, Object> metadata = provider.getArtifactMetadata("unknown");
        assertNotNull(metadata);
        assertTrue(metadata.isEmpty());
    }

    @Test
    void getArtifactThrowsForNonExistent() {
        assertThrows(IllegalStateException.class, () -> provider.getArtifact("nonexistent"));
    }

    @Test
    void saveArtifactPathTraversalInArtifactIdIsSanitized() {
        provider.saveArtifact("../../evil", "content".getBytes(), Map.of());
        byte[] content = provider.getArtifact("../../evil");
        assertNotNull(content);
        assertEquals("content", new String(content));
        assertFalse(tempDir.resolve("..").resolve("evil").toFile().exists(),
                "Sanitized artifact ID must not create real traversal directories");
    }

    @Test
    void presignPathTraversalInObjectKey() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.presign("b", "../secret.txt"));
    }

    @Test
    void presignPathTraversalInBucket() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.presign("../evil", "file.txt"));
    }

    @Test
    void putBucketTraversalDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("../evil", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putBucketTraversalAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("/tmp", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putBucketTraversalBackslash() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("evil\\..\\etc", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putBucketEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putBucketNull() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand(null, "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putBucketSlash() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("my/bucket", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyTraversalDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "../secret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyTraversalNestedDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant/../../secret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "/etc/passwd", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyWindowsPath() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "C:\\Windows\\system.ini", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyUrlEncodedTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant/%2e%2e/secret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyUrlEncodedDoubleDotSlash() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant/%2e%2e%2fsecret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyBackslashTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant\\..\\secret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyNull() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", null, new byte[0], "text/plain")));
    }

    @Test
    void getBucketTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.get("../evil", "file.txt"));
    }

    @Test
    void getObjectKeyTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.get("b", "../secret.txt"));
    }

    @Test
    void deleteBucketTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.delete("../evil", "file.txt"));
    }

    @Test
    void deleteObjectKeyTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.delete("b", "../secret.txt"));
    }

    @Test
    void deleteBucketRootNotAllowed() {
        provider.put(new PutObjectCommand("b", "file.txt", new byte[0], "text/plain"));
        boolean result = provider.delete("b", "file.txt");
        assertTrue(result);
    }

    @Test
    void listObjectsBucketTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.listObjects("../evil", "", 10));
    }

    @Test
    void listObjectsPrefixTraversal() {
        assertEquals(0, provider.listObjects("b", "../evil/", 10).size());
    }

    @Test
    void putFileStaysWithinStorageRoot() throws Exception {
        PutObjectCommand command = new PutObjectCommand(
                "bucket", "sub/dir/file.txt", "safe-content".getBytes(), "text/plain");
        provider.put(command);

        Path stored = tempDir.resolve("bucket").resolve("sub/dir/file.txt");
        assertTrue(Files.exists(stored));
        assertTrue(stored.startsWith(tempDir));
    }

    @Test
    void putBucketWithAllowedSpecialChars() {
        PutObjectCommand command = new PutObjectCommand(
                "my-bucket_1.0", "file.txt", "content".getBytes(), "text/plain");
        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);
        assertEquals("my-bucket_1.0", ref.bucket());
    }

    @Test
    void presignBucketTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.presign("../evil", "file.txt"));
    }

    @Test
    void presignObjectKeyTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.presign("b", "../../etc/passwd"));
    }

    @Test
    void putObjectKeyDoubleEncodedTraversalIsSafe() {
        PutObjectCommand command = new PutObjectCommand(
                "b", "tenant/%252e%252e/secret.txt", "content".getBytes(), "text/plain");
        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);
        Path stored = tempDir.resolve("b").resolve("tenant").resolve("%252e%252e").resolve("secret.txt");
        assertTrue(Files.exists(stored));
        assertFalse(tempDir.resolve("b").resolve("tenant").resolve("..").resolve("secret.txt").toFile().exists());
    }

    @Test
    void putObjectKeyBackslashEncodedTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant/%5c..%5csecret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyDoubleSlashIsSafe() {
        PutObjectCommand command = new PutObjectCommand(
                "b", "tenant//sub//file.txt", "content".getBytes(), "text/plain");
        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);
        assertTrue(Files.exists(tempDir.resolve("b").resolve("tenant/sub/file.txt")));
    }

    @Test
    void putObjectKeyNullByte() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "tenant/\0secret.txt", new byte[0], "text/plain")));
    }

    @Test
    void putObjectKeyNullByteInBucket() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b\0ucket", "file.txt", new byte[0], "text/plain")));
    }

    @Test
    void getObjectKeyNullByte() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.get("b", "dir/\0file.txt"));
    }

    @Test
    void deleteObjectKeyNullByte() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.delete("b", "dir/\0file.txt"));
    }

    @Test
    void putObjectKeyDotSegmentIsSafe() {
        PutObjectCommand command = new PutObjectCommand(
                "b", "dir/.", "content".getBytes(), "text/plain");
        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);
        Path bucketPath = tempDir.resolve("b");
        assertTrue(ref.objectKey().startsWith("dir/"));
        assertFalse(bucketPath.resolve("dir").resolve("..").toFile().exists());
    }

    @Test
    void putObjectKeyDoubleDotOnlySegment() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.put(new PutObjectCommand("b", "..", new byte[0], "text/plain")));
    }

    @Test
    void saveArtifactNullByteInId() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.saveArtifact("art\0id", "content".getBytes(), Map.of()));
    }

    @Test
    void getViaSymlinkEscapesBucket() throws Exception {
        provider.put(new PutObjectCommand("b", "safe/file.txt", "safe".getBytes(), "text/plain"));
        Path bucketPath = tempDir.resolve("b");
        Path symlinkPath = bucketPath.resolve("evil-link");
        Path targetPath = tempDir.resolve("outside-target.txt");
        Files.writeString(targetPath, "outside-content");
        try {
            Files.createSymbolicLink(symlinkPath, targetPath);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }
        assertThrows(IllegalArgumentException.class,
                () -> provider.get("b", "evil-link"));
    }

    @Test
    void deleteViaSymlinkEscapesBucket() throws Exception {
        provider.put(new PutObjectCommand("b", "safe/file.txt", "safe".getBytes(), "text/plain"));
        Path bucketPath = tempDir.resolve("b");
        Path symlinkPath = bucketPath.resolve("evil-link");
        Path targetPath = tempDir.resolve("outside-target2.txt");
        Files.writeString(targetPath, "outside-content");
        try {
            Files.createSymbolicLink(symlinkPath, targetPath);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }
        assertThrows(IllegalArgumentException.class,
                () -> provider.delete("b", "evil-link"));
    }

    // ─── Path-based streaming put tests ───

    @Test
    void putFromPathStoresFileContent() throws Exception {
        Path sourceFile = tempDir.resolve("source.bin");
        byte[] data = "streaming content".getBytes();
        Files.write(sourceFile, data);

        PutObjectCommand command = PutObjectCommand.fromPath(
                "my-bucket", "imports/file.bin", sourceFile, "application/octet-stream");

        assertTrue(command.isFileBased());
        assertNull(command.content());
        assertEquals(sourceFile, command.contentPath());

        StorageObjectRef ref = provider.put(command);

        assertNotNull(ref);
        assertEquals("my-bucket", ref.bucket());
        assertEquals("imports/file.bin", ref.objectKey());

        Path stored = tempDir.resolve("my-bucket").resolve("imports/file.bin");
        assertTrue(Files.exists(stored));
        assertArrayEquals(data, Files.readAllBytes(stored));
    }

    @Test
    void putFromPathCreatesDirectories() throws Exception {
        Path sourceFile = tempDir.resolve("source.txt");
        Files.writeString(sourceFile, "deep path test");

        PutObjectCommand command = PutObjectCommand.fromPath(
                "bucket", "a/b/c/d/file.txt", sourceFile, "text/plain");

        provider.put(command);

        Path stored = tempDir.resolve("bucket").resolve("a/b/c/d/file.txt");
        assertTrue(Files.exists(stored));
        assertEquals("deep path test", Files.readString(stored));
    }

    @Test
    void putFromPathOverwritesExisting() throws Exception {
        Path source1 = tempDir.resolve("src1.txt");
        Path source2 = tempDir.resolve("src2.txt");
        Files.writeString(source1, "version1");
        Files.writeString(source2, "version2");

        provider.put(PutObjectCommand.fromPath("b", "f.txt", source1, "text/plain"));
        provider.put(PutObjectCommand.fromPath("b", "f.txt", source2, "text/plain"));

        Path stored = tempDir.resolve("b").resolve("f.txt");
        assertEquals("version2", Files.readString(stored));
    }

    @Test
    void putFromPathWithLargeFile() throws Exception {
        Path sourceFile = tempDir.resolve("large.bin");
        byte[] data = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < data.length; i++) data[i] = (byte)(i % 256);
        Files.write(sourceFile, data);

        PutObjectCommand command = PutObjectCommand.fromPath(
                "bucket", "large.bin", sourceFile, "application/octet-stream");

        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);

        Path stored = tempDir.resolve("bucket").resolve("large.bin");
        assertTrue(Files.exists(stored));
        assertArrayEquals(data, Files.readAllBytes(stored));
    }

    @Test
    void putFromPathTraversalIsSafe() throws Exception {
        Path sourceFile = tempDir.resolve("safe-source.txt");
        Files.writeString(sourceFile, "safe");

        assertThrows(IllegalArgumentException.class,
                () -> provider.put(PutObjectCommand.fromPath("b", "../evil.txt", sourceFile, "text/plain")));
    }

    @Test
    void byteArrayPutStillWorks() throws Exception {
        PutObjectCommand command = new PutObjectCommand(
                "b", "legacy.txt", "byte array".getBytes(), "text/plain");
        assertFalse(command.isFileBased());

        StorageObjectRef ref = provider.put(command);
        assertNotNull(ref);

        Path stored = tempDir.resolve("b").resolve("legacy.txt");
        assertTrue(Files.exists(stored));
        assertEquals("byte array", Files.readString(stored));
    }
}
