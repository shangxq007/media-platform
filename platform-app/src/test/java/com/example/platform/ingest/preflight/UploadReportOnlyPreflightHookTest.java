package com.example.platform.ingest.preflight;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ingest.contract.UploadPreflightDecision;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UploadReportOnlyPreflightHookTest {

    @Test
    void testDisabledByDefault() {
        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(true);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();
        IngestMetadataMerger merger = new IngestMetadataMerger(() -> tikaProvider, () -> ffprobeProvider);

        UploadReportOnlyPreflightHook hook = new UploadReportOnlyPreflightHook(() -> merger);
        ReflectionTestUtils.setField(hook, "integrationEnabled", false);

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47};
        Optional<?> result = hook.maybeEvaluate(pngBytes, "test.png", "image/png", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testEnabledReportOnly() {
        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(true);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();
        IngestMetadataMerger merger = new IngestMetadataMerger(() -> tikaProvider, () -> ffprobeProvider);

        UploadReportOnlyPreflightHook hook = new UploadReportOnlyPreflightHook(() -> merger);
        ReflectionTestUtils.setField(hook, "integrationEnabled", true);
        ReflectionTestUtils.setField(hook, "failOpen", true);

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = hook.maybeEvaluate(pngBytes, "test.png", "image/png", null);

        assertTrue(result.isPresent());
        assertNotEquals(UploadPreflightDecision.REJECT, result.get().decision());
    }

    @Test
    void testFailOpenOnError() {
        UploadReportOnlyPreflightHook hook = new UploadReportOnlyPreflightHook(() -> null);
        ReflectionTestUtils.setField(hook, "integrationEnabled", true);
        ReflectionTestUtils.setField(hook, "failOpen", true);

        var result = hook.maybeEvaluate(new byte[0], "test.txt", "text/plain", null);

        assertTrue(result.isEmpty());
    }
}
