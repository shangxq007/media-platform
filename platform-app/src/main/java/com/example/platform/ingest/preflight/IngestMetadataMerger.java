package com.example.platform.ingest.preflight;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProvider;
import com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProvider.FFprobeProbeResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Merges Tika generic detection and FFprobe media metadata into unified report-only result.
 * Disabled by default. Not for production upload integration.
 */
@Component
public class IngestMetadataMerger {

    private static final Logger log = LoggerFactory.getLogger(IngestMetadataMerger.class);
    private final TikaDetectorProvider tikaDetector;
    private final FFprobeMediaMetadataProvider ffprobeProvider;

    public IngestMetadataMerger(ObjectProvider<TikaDetectorProvider> tikaProvider,
                                 ObjectProvider<FFprobeMediaMetadataProvider> ffprobeProvider) {
        this.tikaDetector = tikaProvider.getIfAvailable();
        this.ffprobeProvider = ffprobeProvider.getIfAvailable();
    }

    public UploadPreflightResult evaluate(byte[] data, String filename, String declaredContentType, Path localMediaPath) {
        List<IngestWarning> warnings = new ArrayList<>();
        List<DetectorProvenance> provenance = new ArrayList<>();

        // 1. Tika generic detection
        if (tikaDetector != null) {
            var tikaResult = tikaDetector.detect(data, filename, declaredContentType);
            var tikaMetadata = TikaDetectionResultMapper.toIngestMetadataResult(tikaResult);
            warnings.addAll(tikaMetadata.warnings());
            provenance.addAll(tikaMetadata.detectors());
        }

        MediaCategory mediaCategory = MediaCategory.UNKNOWN;
        String detectedContentType = declaredContentType;

        // 2. FFprobe media metadata (if eligible and available)
        MediaTechnicalMetadata mediaTechnicalMetadata = null;
        if (ffprobeProvider != null && localMediaPath != null) {
            FFprobeProbeResult ffprobeResult = ffprobeProvider.probe(localMediaPath, filename, declaredContentType);
            if (ffprobeResult.metadata() != null) {
                mediaTechnicalMetadata = ffprobeResult.metadata();
                mediaCategory = mediaTechnicalMetadata.mediaCategory();
                detectedContentType = mediaTechnicalMetadata.containerFormat();
            }
            if (!ffprobeResult.warnings().isEmpty()) {
                for (String warningCode : ffprobeResult.warnings()) {
                    IngestWarningCode code = mapFfprobeWarning(warningCode);
                    if (code != null) {
                        warnings.add(new IngestWarning(code, IngestWarningSeverity.WARNING,
                            DetectorProviderName.FFPROBE, null, false));
                    }
                }
            }
            provenance.add(new DetectorProvenance(
                DetectorProviderName.FFPROBE, null, DetectorMode.MEDIA_PROBE,
                null, null, true, false, false, true,
                ffprobeResult.durationMs(),
                ffprobeResult.status() == MediaProbeStatus.SUCCESS
                    ? DetectorResultStatus.SUCCESS : DetectorResultStatus.FAILED,
                List.of()
            ));
        }

        // 3. Build merged result
        UploadPreflightDecision decision = warnings.isEmpty()
            ? UploadPreflightDecision.ACCEPT
            : UploadPreflightDecision.ACCEPT_WITH_WARNINGS;

        var metadata = new IngestMetadataResult(
            filename, null, data != null ? (long) data.length : null,
            declaredContentType, detectedContentType, detectedContentType,
            mediaCategory, null, null,
            null, warnings, List.of(), provenance, DetectorProviderName.TIKA,
            java.util.Map.of()
        );

        return new UploadPreflightResult(
            null, null, filename, declaredContentType, data != null ? (long) data.length : null,
            metadata, decision, warnings, List.of(), List.of(),
            provenance, "report-only-merge-v1", java.time.Instant.now()
        );
    }

    private IngestWarningCode mapFfprobeWarning(String warning) {
        return switch (warning) {
            case "MEDIA_PROBE_FAILED" -> IngestWarningCode.MEDIA_TECHNICAL_METADATA_MISSING;
            case "MEDIA_PROBE_TIMEOUT" -> IngestWarningCode.DETECTION_LIMIT_REACHED;
            case "MEDIA_TECHNICAL_METADATA_MISSING" -> IngestWarningCode.MEDIA_TECHNICAL_METADATA_MISSING;
            default -> null;
        };
    }
}
