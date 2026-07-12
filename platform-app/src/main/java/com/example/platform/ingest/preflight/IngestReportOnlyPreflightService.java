package com.example.platform.ingest.preflight;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Report-only ingest preflight service.
 * Disabled by default. Does not reject uploads. Does not change upload acceptance.
 */
@Service
@ConditionalOnProperty(prefix = "ingest.preflight", name = "enabled", havingValue = "true")
public class IngestReportOnlyPreflightService {

    private static final Logger log = LoggerFactory.getLogger(IngestReportOnlyPreflightService.class);
    private final TikaDetectorProvider tikaDetector;

    public IngestReportOnlyPreflightService(TikaDetectorProvider tikaDetector) {
        this.tikaDetector = tikaDetector;
        log.info("IngestReportOnlyPreflightService initialized (REPORT_ONLY - does not reject uploads)");
    }

    public UploadPreflightResult evaluate(byte[] data, String filename, String declaredContentType) {
        var tikaResult = tikaDetector.detect(data, filename, declaredContentType);
        var metadata = TikaDetectionResultMapper.toIngestMetadataResult(tikaResult);

        UploadPreflightDecision decision = metadata.warnings().isEmpty()
            ? UploadPreflightDecision.ACCEPT
            : UploadPreflightDecision.ACCEPT_WITH_WARNINGS;

        return new UploadPreflightResult(
            null, null, filename, declaredContentType, data != null ? (long) data.length : null,
            metadata, decision,
            metadata.warnings(), List.of(), List.of(),
            metadata.detectors(), "report-only-v1", java.time.Instant.now()
        );
    }
}
