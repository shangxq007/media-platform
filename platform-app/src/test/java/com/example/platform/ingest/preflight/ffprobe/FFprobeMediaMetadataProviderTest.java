package com.example.platform.ingest.preflight.ffprobe;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.platform.testsupport.Phase17SandboxConformance.requireCapability;
import static com.example.platform.testsupport.Phase17SandboxConformance.requireSuccessfulProcess;

import com.example.platform.ingest.contract.MediaCategory;
import com.example.platform.ingest.contract.MediaProbeStatus;
import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FFprobeMediaMetadataProviderTest {

    @Test
    void testNonExistentFile() {
        FFprobeMediaMetadataProvider provider = new FFprobeMediaMetadataProvider();
        var result = provider.probe(Path.of("/nonexistent/file.mp4"), "test.mp4", "video/mp4");

        assertEquals(MediaProbeStatus.FAILED, result.status());
        assertNull(result.metadata());
    }

    @Test
    void testInvalidMedia(@TempDir Path tempDir) throws IOException {
        Path invalidFile = tempDir.resolve("invalid.bin");
        Files.write(invalidFile, "not a media file".getBytes());

        FFprobeMediaMetadataProvider provider = new FFprobeMediaMetadataProvider();
        var result = provider.probe(invalidFile, "invalid.bin", "application/octet-stream");

        assertNotNull(result.status());
        assertNotEquals(MediaProbeStatus.SUCCESS, result.status());
        assertNotNull(result.warnings());
    }

    @Test
    void testEmptyFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.write(emptyFile, new byte[0]);

        FFprobeMediaMetadataProvider provider = new FFprobeMediaMetadataProvider();
        var result = provider.probe(emptyFile, "empty.bin", "application/octet-stream");

        assertNotNull(result.status());
        assertNotEquals(MediaProbeStatus.SUCCESS, result.status());
    }

    @Test
    void testValidVideoIfFFprobeAvailable(@TempDir Path tempDir) throws IOException {
        var sandboxDetection = BubblewrapSandboxCapabilityDetector.detect();
        requireCapability(sandboxDetection.launcher().isPresent(),
                "Enforceable host sandbox unavailable: " + sandboxDetection.diagnostic());

        requireSuccessfulProcess(java.util.List.of("ffprobe", "-version"),
                "FFprobe binary");

        // Generate a tiny test video using FFmpeg
        Path testVideo = tempDir.resolve("test.mp4");
        requireSuccessfulProcess(java.util.List.of(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=blue:s=320x240:d=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                testVideo.toString()
            ), "FFmpeg fixture generation");

        FFprobeMediaMetadataProvider provider = new FFprobeMediaMetadataProvider();
        var result = provider.probe(testVideo, "test.mp4", "video/mp4");

        assertEquals(MediaProbeStatus.SUCCESS, result.status());
        assertNotNull(result.metadata());
        assertEquals(MediaCategory.VIDEO, result.metadata().mediaCategory());
        assertTrue(result.metadata().hasVideo());
        assertNotNull(result.metadata().width());
        assertNotNull(result.metadata().height());
    }
}
