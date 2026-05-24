package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaProbeServiceTest {

    private MediaProbeService probeService;

    @BeforeEach
    void setUp() {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        probeService = new MediaProbeService(adapter);
    }

    @Test
    void probeNonexistentFileReturnsFailed(@TempDir Path tempDir) {
        probeService.setStorageRoot(tempDir.toString());

        MediaProbeResult result = probeService.probe("job-1", "artifacts/job-1/output.mp4");

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("File not found"));
    }

    @Test
    void probeValidVideoReturnsResult(@TempDir Path tempDir) {
        probeService.setStorageRoot(tempDir.toString());

        JavaCVRenderService renderService = new JavaCVRenderService(probeService);
        JavaCVTranscodeService transcodeService = new JavaCVTranscodeService(probeService);
        JavaCVRenderProvider provider = new JavaCVRenderProvider(renderService, transcodeService,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult renderResult = provider.render("job-1", "{\"tracks\":[]}", "default_720p");

        MediaProbeResult result = probeService.probe("job-1", "artifacts/job-1/output.mp4");

        assertTrue(result.valid(), "Result should be valid: " + result.errorMessage());
        assertTrue(result.hasVideo());
        assertEquals(1280, result.width());
        assertEquals(720, result.height());
        assertNotNull(result.videoCodec());
        assertTrue(result.fileSizeBytes() > 0);
    }

    @Test
    void probeAbsoluteDelegatesToAdapter(@TempDir Path tempDir) {
        probeService.setStorageRoot(tempDir.toString());

        MediaProbeResult result = probeService.probeAbsolute("job-1", "/nonexistent/file.mp4");

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("File not found"));
    }
}
