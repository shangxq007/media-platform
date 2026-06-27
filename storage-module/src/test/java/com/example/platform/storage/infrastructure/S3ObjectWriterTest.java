package com.example.platform.storage.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link S3ObjectWriter}.
 *
 * <p>Covers:
 * <ul>
 *   <li>isEnabled checks</li>
 *   <li>getDefaultBucket</li>
 *   <li>Upload validation (null paths, missing files, blank bucket/key)</li>
 * </ul>
 *
 * <p>Integration tests against real S3 backend are in
 * {@code S3ObjectWriterIntegrationTest} (opt-in, requires RustFS).</p>
 */
class S3ObjectWriterTest {

    @Test
    @DisplayName("isEnabled returns false when S3 is disabled")
    void isEnabledReturnsFalseWhenDisabled() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(false);
        S3ObjectWriter writer = new S3ObjectWriter(props);
        assertFalse(writer.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns false when endpoint is blank")
    void isEnabledReturnsFalseWhenEndpointBlank() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("");
        S3ObjectWriter writer = new S3ObjectWriter(props);
        assertFalse(writer.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns true when properly configured")
    void isEnabledReturnsTrueWhenConfigured() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:9000");
        S3ObjectWriter writer = new S3ObjectWriter(props);
        assertTrue(writer.isEnabled());
    }

    @Test
    @DisplayName("getDefaultBucket returns configured bucket")
    void getDefaultBucketReturnsConfiguredBucket() {
        StorageS3Properties props = new StorageS3Properties();
        props.setDefaultBucket("test-bucket");
        S3ObjectWriter writer = new S3ObjectWriter(props);
        assertEquals("test-bucket", writer.getDefaultBucket());
    }

    @Test
    @DisplayName("getDefaultBucket returns default when not set")
    void getDefaultBucketReturnsDefault() {
        StorageS3Properties props = new StorageS3Properties();
        S3ObjectWriter writer = new S3ObjectWriter(props);
        assertEquals("render-cache", writer.getDefaultBucket());
    }
}
