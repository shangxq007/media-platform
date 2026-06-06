package com.example.platform.identity.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetadataScrubberTest {

    private final MetadataScrubber scrubber = new MetadataScrubber();

    @Test
    void shouldRemoveDownloadUrl() {
        // Given
        String json = "{\"name\":\"test\",\"downloadUrl\":\"https://example.com/file.mp4\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("downloadUrl"));
        assertFalse(result.contains("example.com"));
        assertTrue(result.contains("name"));
    }

    @Test
    void shouldRemoveStorageUri() {
        // Given
        String json = "{\"name\":\"test\",\"storageUri\":\"s3://bucket/key\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("storageUri"));
        assertFalse(result.contains("s3://"));
    }

    @Test
    void shouldRemoveStorageRef() {
        // Given
        String json = "{\"name\":\"test\",\"storageRef\":\"ref-123\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("storageRef"));
    }

    @Test
    void shouldRemoveBucket() {
        // Given
        String json = "{\"name\":\"test\",\"bucket\":\"my-bucket\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("bucket"));
    }

    @Test
    void shouldRemoveKey() {
        // Given
        String json = "{\"name\":\"test\",\"key\":\"path/to/file.mp4\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("\"key\""));
    }

    @Test
    void shouldRemoveSignedUrl() {
        // Given
        String json = "{\"name\":\"test\",\"signedUrl\":\"https://signed.example.com/file.mp4?token=abc\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("signedUrl"));
        assertFalse(result.contains("signed.example.com"));
    }

    @Test
    void shouldRemoveUrl() {
        // Given
        String json = "{\"name\":\"test\",\"url\":\"https://example.com/file.mp4\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("url"));
        assertFalse(result.contains("example.com"));
    }

    @Test
    void shouldPreserveSourceAssetId() {
        // Given
        String json = "{\"sourceAssetId\":\"art-123\",\"targetAssetId\":null,\"status\":\"needs_upload\"}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("sourceAssetId"));
        assertTrue(result.contains("art-123"));
        assertTrue(result.contains("needs_upload"));
    }

    @Test
    void shouldPreserveTimelineStructure() {
        // Given
        String json = "{\"id\":\"tl-1\",\"name\":\"Timeline\",\"tracks\":[{\"id\":\"v1\",\"name\":\"Video 1\"}]}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("id"));
        assertTrue(result.contains("tl-1"));
        assertTrue(result.contains("tracks"));
        assertTrue(result.contains("v1"));
    }

    @Test
    void shouldPreserveEffectKey() {
        // Given
        String json = "{\"effectKey\":\"video.fade_in\",\"params\":{\"duration\":1.0}}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("effectKey"));
        assertTrue(result.contains("video.fade_in"));
    }

    @Test
    void shouldPreserveSpatialCoordinates() {
        // Given
        String json = "{\"x\":100,\"y\":200,\"width\":1920,\"height\":1080}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("x"));
        assertTrue(result.contains("100"));
        assertTrue(result.contains("width"));
        assertTrue(result.contains("1920"));
    }

    @Test
    void shouldHandleNestedSensitiveFields() {
        // Given
        String json = "{\"clips\":[{\"id\":\"c1\",\"downloadUrl\":\"https://example.com/clip1.mp4\"}]}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertFalse(result.contains("downloadUrl"));
        assertFalse(result.contains("example.com"));
        assertTrue(result.contains("clips"));
        assertTrue(result.contains("c1"));
    }

    @Test
    void shouldReturnNullForNullInput() {
        // When
        String result = scrubber.scrub(null);

        // Then
        assertNull(result);
    }

    @Test
    void shouldReturnNullForBlankInput() {
        // When
        String result = scrubber.scrub("   ");

        // Then
        assertNull(result);
    }

    @Test
    void shouldReturnNullForInvalidJson() {
        // Given
        String json = "not valid json";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNull(result);
    }

    @Test
    void shouldHandleEmptyObject() {
        // Given
        String json = "{}";

        // When
        String result = scrubber.scrub(json);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("{"));
        assertTrue(result.contains("}"));
    }
}
