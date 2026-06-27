package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link S3ObjectMaterializer}.
 *
 * <p>Tests the materialization logic using real S3 configuration but
 * without requiring a running S3 backend. Integration tests against
 * RustFS are in a separate opt-in test class.</p>
 */
class S3ObjectMaterializerTest {

    @Test
    @DisplayName("isEnabled returns false when endpoint is null")
    void isEnabledReturnsFalseWhenEndpointNull() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint(null);
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);
        assertFalse(materializer.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns false when endpoint is blank")
    void isEnabledReturnsFalseWhenEndpointBlank() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("   ");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);
        assertFalse(materializer.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns false when not enabled")
    void isEnabledReturnsFalseWhenNotEnabled() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(false);
        props.setEndpoint("http://localhost:9000");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);
        assertFalse(materializer.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns true when properly configured")
    void isEnabledReturnsTrueWhenConfigured() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        props.setRegion("us-east-1");
        props.setAccessKey("test-key");
        props.setSecretKey("test-secret");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);
        assertTrue(materializer.isEnabled());
    }

    @Test
    @DisplayName("materialize returns empty for null bucket")
    void materializeReturnsEmptyForNullBucket() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

        Optional<S3ObjectMaterializer.MaterializedObject> result =
                materializer.materialize(null, "some-key", null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("materialize returns empty for blank bucket")
    void materializeReturnsEmptyForBlankBucket() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

        Optional<S3ObjectMaterializer.MaterializedObject> result =
                materializer.materialize("  ", "some-key", null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("materialize returns empty for null key")
    void materializeReturnsEmptyForNullKey() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

        Optional<S3ObjectMaterializer.MaterializedObject> result =
                materializer.materialize("bucket", null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("materialize returns empty for blank key")
    void materializeReturnsEmptyForBlankKey() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

        Optional<S3ObjectMaterializer.MaterializedObject> result =
                materializer.materialize("bucket", "  ", null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("MaterializedObject record holds values correctly")
    void materializedObjectRecordValues(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, "hello world".getBytes());

        S3ObjectMaterializer.MaterializedObject obj =
                new S3ObjectMaterializer.MaterializedObject(testFile, 11, "abc123", "text/plain");

        assertEquals(testFile, obj.localPath());
        assertEquals(11, obj.sizeBytes());
        assertEquals("abc123", obj.checksum());
        assertEquals("text/plain", obj.contentType());
    }
}
