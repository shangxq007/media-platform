package com.example.platform.ingest.contract;

import java.time.Instant;
import java.util.List;

public record UploadPreflightResult(
    String tenantId,
    String projectId,
    String sourceFilename,
    String declaredContentType,
    Long sizeBytes,
    IngestMetadataResult metadata,
    UploadPreflightDecision decision,
    List<IngestWarning> warnings,
    List<IngestRejectionReason> rejectionReasons,
    List<String> userSafeMessages,
    List<DetectorProvenance> detectorProvenance,
    String policyVersion,
    Instant evaluatedAt
) {}
