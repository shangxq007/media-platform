package com.example.platform.ingest.contract;

import java.time.Instant;
import java.util.List;

public record SafePreflightReportSummary(
    String reportId,
    String tenantId,
    String projectId,
    String uploadId,
    String rawMediaProductId,
    PreflightPolicyMode mode,
    String policyProfile,
    UploadPreflightDecision decision,
    boolean reportOnly,
    boolean failOpen,
    MediaCategory mediaCategory,
    ContentTypeSummary contentTypeSummary,
    List<IngestWarningCode> warningCodes,
    List<String> policyFindingCodes,
    List<IngestRejectionReasonCode> rejectionCandidateCodes,
    List<DetectorSummary> detectorSummaries,
    SafeMediaSummary mediaSummary,
    Instant createdAt,
    Instant retentionUntil
) {}
