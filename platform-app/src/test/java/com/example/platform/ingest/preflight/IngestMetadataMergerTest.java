package com.example.platform.ingest.preflight;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.platform.testsupport.Phase17SandboxConformance.requireCapability;
import static com.example.platform.testsupport.Phase17SandboxConformance.requireSuccessfulProcess;
import static org.mockito.Mockito.*;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProvider;
import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class IngestMetadataMergerTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider(T instance) {
        ObjectProvider<T> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }

    @Test
    void testTikaOnlyMerge() {
        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(true);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();

        IngestMetadataMerger merger = new IngestMetadataMerger(
            mockProvider(tikaProvider), mockProvider(ffprobeProvider));

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = merger.evaluate(pngBytes, "image.png", "image/png", null);

        assertEquals(UploadPreflightDecision.ACCEPT, result.decision());
        assertNotNull(result.metadata());
        assertNotNull(result.detectorProvenance());
    }

    @Test
    void testNoRejectionEnforced() {
        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(true);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();

        IngestMetadataMerger merger = new IngestMetadataMerger(
            mockProvider(tikaProvider), mockProvider(ffprobeProvider));

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = merger.evaluate(pngBytes, "image.txt", "text/plain", null);

        assertNotEquals(UploadPreflightDecision.REJECT, result.decision());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void testFfprobeForVideo(@TempDir Path tempDir) throws IOException, InterruptedException {
        var sandboxDetection = BubblewrapSandboxCapabilityDetector.detect();
        requireCapability(sandboxDetection.launcher().isPresent(),
            "Enforceable host sandbox unavailable: " + sandboxDetection.diagnostic());

        requireSuccessfulProcess(java.util.List.of("ffmpeg", "-version"),
                "FFmpeg binary");

        // Generate tiny test video
        Path testVideo = tempDir.resolve("test.mp4");
        requireSuccessfulProcess(java.util.List.of(
            "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=blue:s=320x240:d=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", testVideo.toString()
        ), "FFmpeg fixture generation");

        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(true);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();

        IngestMetadataMerger merger = new IngestMetadataMerger(
            mockProvider(tikaProvider), mockProvider(ffprobeProvider));

        var result = merger.evaluate(new byte[16], "test.mp4", "video/mp4", testVideo);

        assertEquals(UploadPreflightDecision.ACCEPT, result.decision());
        assertEquals(2, result.detectorProvenance().size());
    }
}
