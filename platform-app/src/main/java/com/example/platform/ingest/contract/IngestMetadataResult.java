package com.example.platform.ingest.contract;

import java.util.List;
import java.util.Map;

public record IngestMetadataResult(
    String sourceFilename,
    String normalizedExtension,
    Long sizeBytes,
    String declaredContentType,
    String detectedContentType,
    String normalizedContentType,
    MediaCategory mediaCategory,
    Boolean extensionMatchesDetectedType,
    Boolean declaredMatchesDetectedType,
    Boolean acceptedByPolicy,
    List<IngestWarning> warnings,
    List<IngestRejectionReason> rejectionReasons,
    List<DetectorProvenance> detectors,
    DetectorProviderName primaryDetector,
    Map<String, String> safeMetadata
) {}
