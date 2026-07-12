package com.example.platform.ingest.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngestContractTest {

    @Test
    void mediaCategoryHas10Values() {
        assertEquals(10, MediaCategory.values().length);
    }

    @Test
    void warningCodeHas17Values() {
        assertEquals(17, IngestWarningCode.values().length);
    }

    @Test
    void rejectionReasonCodeHas18Values() {
        assertEquals(18, IngestRejectionReasonCode.values().length);
    }

    @Test
    void preflightDecisionHas5Values() {
        assertEquals(5, UploadPreflightDecision.values().length);
    }

    @Test
    void buildValidMp4Result() {
        var result = new IngestMetadataResult(
            "clip.mp4", "mp4", 1024L,
            "video/mp4", "video/mp4", "video/mp4",
            MediaCategory.VIDEO,
            true, true, true,
            List.of(), List.of(),
            List.of(new DetectorProvenance(
                DetectorProviderName.TIKA, "2.9.2", DetectorMode.DETECTOR_ONLY,
                8192L, 8192L, true, true, true, false,
                50L, DetectorResultStatus.SUCCESS, List.of()
            )),
            DetectorProviderName.TIKA,
            java.util.Map.of()
        );
        assertEquals(MediaCategory.VIDEO, result.mediaCategory());
        assertTrue(result.extensionMatchesDetectedType());
        assertTrue(result.declaredMatchesDetectedType());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void buildMismatchResult() {
        var result = new IngestMetadataResult(
            "image.txt", "txt", 100L,
            "text/plain", "image/png", "image/png",
            MediaCategory.IMAGE,
            false, false, null,
            List.of(
                new IngestWarning(IngestWarningCode.EXTENSION_CONTENT_TYPE_MISMATCH,
                    IngestWarningSeverity.WARNING, DetectorProviderName.TIKA, null, true),
                new IngestWarning(IngestWarningCode.DECLARED_CONTENT_TYPE_MISMATCH,
                    IngestWarningSeverity.WARNING, DetectorProviderName.TIKA, null, true)
            ),
            List.of(),
            List.of(),
            DetectorProviderName.TIKA,
            java.util.Map.of()
        );
        assertFalse(result.extensionMatchesDetectedType());
        assertFalse(result.declaredMatchesDetectedType());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void userSafeMessagesExist() {
        assertNotNull(IngestRejectionMessages.userSafeMessage(IngestRejectionReasonCode.FILE_EMPTY));
        assertNotNull(IngestRejectionMessages.userSafeMessage(IngestRejectionReasonCode.FILE_TOO_LARGE));
        assertNotNull(IngestRejectionMessages.userSafeMessage(IngestRejectionReasonCode.CONTENT_TYPE_UNSUPPORTED));
    }
}
