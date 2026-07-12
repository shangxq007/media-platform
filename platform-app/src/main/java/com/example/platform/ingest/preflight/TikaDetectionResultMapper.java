package com.example.platform.ingest.preflight;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TikaDetectionResultMapper {

    private TikaDetectionResultMapper() {}

    public static IngestMetadataResult toIngestMetadataResult(TikaDetectionResult tikaResult) {
        String detectedType = tikaResult.detectedContentType();
        MediaCategory category = classifyMediaCategory(detectedType);

        List<IngestWarning> warnings = new ArrayList<>();
        for (String warning : tikaResult.warnings()) {
            IngestWarningCode code = mapWarningCode(warning);
            if (code != null) {
                warnings.add(new IngestWarning(
                    code, IngestWarningSeverity.WARNING, DetectorProviderName.TIKA, null, true));
            }
        }

        DetectorProvenance provenance = new DetectorProvenance(
            DetectorProviderName.TIKA, "2.9.2", DetectorMode.DETECTOR_ONLY,
            8192L, null, true, true, true, false,
            null, DetectorResultStatus.SUCCESS, List.of()
        );

        return new IngestMetadataResult(
            tikaResult.filename(),
            tikaResult.extension(),
            null,
            tikaResult.declaredContentType(),
            detectedType,
            detectedType,
            category,
            tikaResult.extensionMatchesDetectedType(),
            tikaResult.declaredMatchesDetectedType(),
            null,
            warnings,
            List.of(),
            List.of(provenance),
            DetectorProviderName.TIKA,
            Map.of()
        );
    }

    private static MediaCategory classifyMediaCategory(String contentType) {
        if (contentType == null) return MediaCategory.UNKNOWN;
        return switch (contentType) {
            case String s when s.startsWith("video/") -> MediaCategory.VIDEO;
            case String s when s.startsWith("audio/") -> MediaCategory.AUDIO;
            case String s when s.startsWith("image/") -> MediaCategory.IMAGE;
            case String s when s.startsWith("text/") -> MediaCategory.TEXT;
            case "application/pdf" -> MediaCategory.DOCUMENT;
            case String s when s.startsWith("application/zip") -> MediaCategory.ARCHIVE;
            default -> MediaCategory.UNKNOWN;
        };
    }

    private static IngestWarningCode mapWarningCode(String warning) {
        return switch (warning) {
            case "CONTENT_TYPE_MISMATCH" -> IngestWarningCode.DECLARED_CONTENT_TYPE_MISMATCH;
            case "EXTENSION_MISMATCH" -> IngestWarningCode.EXTENSION_CONTENT_TYPE_MISMATCH;
            case "UNKNOWN_CONTENT_TYPE" -> IngestWarningCode.UNKNOWN_CONTENT_TYPE;
            default -> null;
        };
    }
}
