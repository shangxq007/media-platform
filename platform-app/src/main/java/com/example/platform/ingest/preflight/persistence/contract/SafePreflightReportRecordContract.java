package com.example.platform.ingest.preflight.persistence.contract;

import com.example.platform.ingest.contract.*;
import java.time.Instant;
import java.util.List;

public record SafePreflightReportRecordContract(
    String recordId,
    String tenantId,
    String projectId,
    String rawMediaProductId,
    String uploadAttemptId,
    Instant createdAt,
    Instant expiresAt,
    SafePreflightPersistenceMode mode,
    SafePreflightPersistenceAccessScope accessScope,
    SafePreflightPersistenceLifecycleState lifecycleState,
    boolean reportOnlyMode,
    boolean failOpen,
    UploadPreflightDecision overallDecision,
    int warningCount,
    int findingCount,
    int rejectCandidateCount,
    SafeContentTypeRecord contentType,
    List<SafeDetectorRecord> detectors,
    SafeMediaRecord media,
    List<IngestWarningCode> warningCodes,
    List<String> findingCodes,
    List<String> userSafeMessageCodes,
    String policyProfile,
    PreflightPolicyMode policyMode
) {
    public record SafeContentTypeRecord(
        String declaredMime,
        String detectedMime,
        boolean mimeMismatch
    ) {}

    public record SafeDetectorRecord(
        String detectorName,
        String detectorVersion,
        boolean success,
        List<String> warningCodes
    ) {}

    public record SafeMediaRecord(
        Long durationMs,
        Integer width,
        Integer height,
        String containerFormat,
        String videoCodec,
        String audioCodec,
        boolean hasVideo,
        boolean hasAudio
    ) {}
}
