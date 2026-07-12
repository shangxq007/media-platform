package com.example.platform.ingest.preflight.ffprobe;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ingest.contract.MediaCategory;
import com.example.platform.ingest.contract.MediaProbeStatus;
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
        // This test requires FFprobe binary - skip if not available
        try {
            Process p = new ProcessBuilder("ffprobe", "-version").start();
            p.waitFor();
            if (p.exitValue() != 0) return;
        } catch (Exception e) {
            return; // FFprobe not available
        }

        // Generate a tiny test video using FFmpeg
        Path testVideo = tempDir.resolve("test.mp4");
        try {
            Process ffmpeg = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=blue:s=320x240:d=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                testVideo.toString()
            ).start();
            ffmpeg.waitFor();
            if (ffmpeg.exitValue() != 0) return;
        } catch (Exception e) {
            return; // FFmpeg not available
        }

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
