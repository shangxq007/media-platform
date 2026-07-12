package com.example.platform.ingest.preflight;

import com.example.platform.ingest.contract.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps UploadPreflightResult to SafePreflightReportSummary.
 * No raw metadata. No storage internals.
 */
public final class SafePreflightReportMapper {

    private SafePreflightReportMapper() {}

    public static SafePreflightReportSummary fromPreflightResult(UploadPreflightResult result) {
        if (result == null) return null;

        ContentTypeSummary contentType = null;
        if (result.metadata() != null) {
            contentType = new ContentTypeSummary(
                result.metadata().declaredContentType(),
                result.metadata().detectedContentType(),
                result.metadata().normalizedContentType(),
                result.metadata().normalizedExtension(),
                result.metadata().extensionMatchesDetectedType(),
                result.metadata().declaredMatchesDetectedType()
            );
        }

        List<DetectorSummary> detectorSummaries = new ArrayList<>();
        if (result.metadata() != null) {
            for (var provenance : result.metadata().detectors()) {
                detectorSummaries.add(new DetectorSummary(
                    provenance.provider(), provenance.mode(), provenance.resultStatus(), provenance.durationMs()
                ));
            }
        }

        return new SafePreflightReportSummary(
            null, null, null, null, null,
            PreflightPolicyMode.REPORT_ONLY, "report-only-v1",
            result.decision(), true, true,
            result.metadata() != null ? result.metadata().mediaCategory() : MediaCategory.UNKNOWN,
            contentType,
            result.warnings() != null ? result.warnings().stream().map(IngestWarning::code).toList() : List.of(),
            List.of(),
            result.rejectionReasons() != null ? result.rejectionReasons().stream().map(IngestRejectionReason::code).toList() : List.of(),
            detectorSummaries,
            null,
            Instant.now(), null
        );
    }
}
