package com.example.platform.ingest.preflight;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import org.junit.jupiter.api.Test;

class IngestReportOnlyPreflightServiceTest {

    @Test
    void testCleanPngReport() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);
        TikaDetectorProvider provider = new TikaDetectorProvider(props);
        IngestReportOnlyPreflightService service = new IngestReportOnlyPreflightService(provider);

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = service.evaluate(pngBytes, "image.png", "image/png");

        assertEquals(UploadPreflightDecision.ACCEPT, result.decision());
        assertEquals(MediaCategory.IMAGE, result.metadata().mediaCategory());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void testNoRejectionEnforced() {
        TikaExperimentalProperties props = new TikaExperimentalProperties();
        props.setEnabled(true);
        TikaDetectorProvider provider = new TikaDetectorProvider(props);
        IngestReportOnlyPreflightService service = new IngestReportOnlyPreflightService(provider);

        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = service.evaluate(pngBytes, "image.txt", "text/plain");

        assertNotEquals(UploadPreflightDecision.REJECT, result.decision());
        assertTrue(result.rejectionReasons().isEmpty());
    }
}
