package com.example.platform.ingest.experimental.tika;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tika detector provider smoke tests.
 */
class TikaDetectorProviderTest {

    @Test
    void testPngDetection() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);
        props.setMaxDetectBytes(8192);

        TikaDetectorProvider provider = new TikaDetectorProvider(props);

        // PNG magic bytes
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};

        TikaDetectionResult result = provider.detect(pngBytes, "test.png", "image/png");

        assertNotNull(result);
        assertEquals("image/png", result.detectedContentType());
        assertTrue(result.declaredMatchesDetectedType());
    }

    @Test
    void testTextDetection() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);

        TikaDetectorProvider provider = new TikaDetectorProvider(props);

        byte[] textBytes = "Hello World".getBytes();

        TikaDetectionResult result = provider.detect(textBytes, "test.txt", "text/plain");

        assertNotNull(result);
        assertTrue(result.detectedContentType().startsWith("text/"));
    }

    @Test
    void testContentTypeMismatch() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);

        TikaDetectorProvider provider = new TikaDetectorProvider(props);

        // PNG bytes but declared as text/plain
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        TikaDetectionResult result = provider.detect(pngBytes, "test.png", "text/plain");

        assertNotNull(result);
        assertFalse(result.declaredMatchesDetectedType());
        assertTrue(result.warnings().contains("CONTENT_TYPE_MISMATCH"));
    }

    @Test
    void testEmptyInput() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);

        TikaDetectorProvider provider = new TikaDetectorProvider(props);

        TikaDetectionResult result = provider.detect(new byte[0], "test.txt", "text/plain");

        assertNotNull(result);
        assertEquals("application/octet-stream", result.detectedContentType());
    }

    @Test
    void testNullInput() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);

        TikaDetectorProvider provider = new TikaDetectorProvider(props);

        TikaDetectionResult result = provider.detect(null, "test.txt", "text/plain");

        assertNotNull(result);
        assertEquals("application/octet-stream", result.detectedContentType());
    }
}
