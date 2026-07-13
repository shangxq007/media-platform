package com.example.platform.ingest.preflight.persistence.read;

import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecord;
import org.springframework.stereotype.Component;

@Component
public class SafePreflightReportReadMapper {

    public SafePreflightReportRecordListItem toListItem(SafePreflightReportRecord record) {
        return new SafePreflightReportRecordListItem(
            record.id(),
            record.rawMediaProductId(),
            record.uploadAttemptId(),
            record.createdAt(),
            record.expiresAt(),
            record.lifecycleState(),
            record.persistenceMode(),
            record.accessScope(),
            record.overallDecision(),
            record.warningCount(),
            record.findingCount(),
            record.rejectCandidateCount(),
            record.policyProfile(),
            record.policyMode(),
            record.policyDecision(),
            record.policyFindingCount(),
            record.policyRejectCandidateCount(),
            record.uploadContinues(),
            record.blocking()
        );
    }

    public SafePreflightReportRecordDetailResponse toDetailResponse(SafePreflightReportRecord record) {
        return new SafePreflightReportRecordDetailResponse(
            record.id(),
            record.tenantId(),
            record.projectId(),
            record.rawMediaProductId(),
            record.uploadAttemptId(),
            record.createdAt(),
            record.expiresAt(),
            record.lifecycleState(),
            record.persistenceMode(),
            record.accessScope(),
            record.retentionDays(),
            record.reportOnlyMode(),
            record.failOpen(),
            record.overallDecision(),
            record.warningCount(),
            record.findingCount(),
            record.rejectCandidateCount(),
            record.declaredMime(),
            record.detectedMime(),
            record.mimeMismatch(),
            record.contentTypeConfidence(),
            record.durationMs(),
            record.width(),
            record.height(),
            record.containerFormat(),
            record.videoCodec(),
            record.audioCodec(),
            record.hasVideo(),
            record.hasAudio(),
            record.tikaDetectorSuccess(),
            record.ffprobeDetectorSuccess(),
            record.detectorWarningCodes(),
            record.policyProfile(),
            record.policyMode(),
            record.policyDecision(),
            record.policyFindingCount(),
            record.policyRejectCandidateCount(),
            record.policyUserSafeMessageCodes(),
            record.policyFindingCodes(),
            record.uploadContinues(),
            record.blocking(),
            record.redactedAt(),
            record.expiredAt(),
            record.deletedAt(),
            record.schemaVersion()
        );
    }
}
