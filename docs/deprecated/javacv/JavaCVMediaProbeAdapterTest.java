package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-media")
class JavaCVMediaProbeAdapterTest {

    private JavaCVMediaProbeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JavaCVMediaProbeAdapter();
    }

    @Test
    void isAvailableReturnsTrue() {
        assertTrue(adapter.isAvailable());
    }

    @Test
    void probeNonexistentFileReturnsFailed(@TempDir Path tempDir) {
        MediaProbeResult result = adapter.probe("job-1", tempDir.resolve("nonexistent.mp4").toString());

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("File not found"));
    }

    @Test
    void probeValidVideoReturnsResult(@TempDir Path tempDir) {
        MediaProbeService probeService = new MediaProbeService(adapter);
        probeService.setStorageRoot(tempDir.toString());

        JavaCVRenderService renderService = new JavaCVRenderService(probeService);
        JavaCVTranscodeService transcodeService = new JavaCVTranscodeService(probeService);
        JavaCVRenderProvider provider = new JavaCVRenderProvider(renderService, transcodeService,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        provider.setStorageRoot(tempDir.toString());

        provider.render("job-1", "{\"tracks\":[]}", "default_720p");

        MediaProbeResult result = adapter.probe("job-1", tempDir.resolve("artifacts/job-1/output.mp4").toString());

        assertTrue(result.valid(), "Result should be valid: " + result.errorMessage());
        assertTrue(result.hasVideo());
        assertEquals(1280, result.width());
        assertEquals(720, result.height());
        assertNotNull(result.videoCodec());
        assertTrue(result.fileSizeBytes() > 0);
    }

    @Test
    void probeFailedReturnsErrorMessage() {
        MediaProbeResult result = adapter.probe("job-x", "/tmp/definitely-not-a-real-video-12345.mp4");

        assertFalse(result.valid());
        assertNotNull(result.errorMessage());
    }
}
