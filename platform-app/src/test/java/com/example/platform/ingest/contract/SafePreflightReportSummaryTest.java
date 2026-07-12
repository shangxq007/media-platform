package com.example.platform.ingest.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafePreflightReportSummaryTest {

    @Test
    void testCleanReport() {
        var summary = new SafePreflightReportSummary(
            "rpt-001", "ten-001", "prj-001", null, null,
            PreflightPolicyMode.REPORT_ONLY, "preview-report-only",
            UploadPreflightDecision.ACCEPT, true, true,
            MediaCategory.VIDEO,
            new ContentTypeSummary("video/mp4", "video/mp4", "video/mp4", "mp4", true, true),
            List.of(), List.of(), List.of(),
            List.of(new DetectorSummary(DetectorProviderName.TIKA, DetectorMode.DETECTOR_ONLY, DetectorResultStatus.SUCCESS, 50L)),
            new SafeMediaSummary(5000L, "mp4", 5000000L, true, true, false, 1, 1, 0, "h264", "aac", 1920, 1080, null, 44100, 2, 0, MediaProbeStatus.SUCCESS),
            Instant.now(), Instant.now().plusSeconds(86400 * 30)
        );

        assertEquals("rpt-001", summary.reportId());
        assertEquals(MediaCategory.VIDEO, summary.mediaCategory());
        assertTrue(summary.reportOnly());
        assertTrue(summary.failOpen());
        assertNull(summary.rawMediaProductId());
    }

    @Test
    void testNoSensitiveFields() {
        var summary = new SafePreflightReportSummary(
            "rpt-002", "ten-001", "prj-001", null, null,
            PreflightPolicyMode.REPORT_ONLY, "preview-report-only",
            UploadPreflightDecision.ACCEPT_WITH_WARNINGS, true, true,
            MediaCategory.UNKNOWN,
            new ContentTypeSummary("text/plain", "image/png", "image/png", "txt", false, false),
            List.of(IngestWarningCode.EXTENSION_CONTENT_TYPE_MISMATCH),
            List.of(), List.of(), List.of(), null,
            Instant.now(), null
        );

        assertEquals(1, summary.warningCodes().size());
        assertNull(summary.uploadId());
        assertNull(summary.rawMediaProductId());
    }
}
