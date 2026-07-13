package com.example.platform.ingest.preflight.persistence.writer;

import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecord;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractProperties;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceAccessScope;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceLifecycleState;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceMode;
import java.time.Instant;
import com.example.platform.ingest.contract.DetectorResultStatus;
import org.springframework.stereotype.Component;

@Component
public class SafePreflightReportRecordMapper {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public SafePreflightReportRecord map(SafePreflightPersistenceWriteRequest request,
                                          SafePreflightPersistenceContractProperties config) {
        Instant expiresAt = request.createdAt().plusSeconds(config.getRetentionDays() * 86400L);

        var safeReport = request.safeReport();
        var policyResult = request.policyResult();
        var contentType = safeReport.contentTypeSummary();
        var media = safeReport.mediaSummary();

        return new SafePreflightReportRecord(
            null, // id auto-generated
            request.tenantId(),
            request.projectId(),
            request.rawMediaProductId(),
            request.uploadAttemptId(),
            request.createdAt(),
            expiresAt,
            SafePreflightPersistenceLifecycleState.RECORDED.name(),
            SafePreflightPersistenceMode.DEV_PREVIEW_EPHEMERAL_ONLY.name(),
            SafePreflightPersistenceAccessScope.DEV_ONLY.name(),
            config.getRetentionDays(),
            true, // reportOnlyMode
            true, // failOpen
            safeReport.decision() != null ? safeReport.decision().name() : null,
            safeReport.warningCodes() != null ? safeReport.warningCodes().size() : 0,
            safeReport.policyFindingCodes() != null ? safeReport.policyFindingCodes().size() : 0,
            safeReport.rejectionCandidateCodes() != null ? safeReport.rejectionCandidateCodes().size() : 0,
            contentType != null ? contentType.declaredContentType() : null,
            contentType != null ? contentType.detectedContentType() : null,
            contentType != null && Boolean.TRUE.equals(contentType.declaredMatchesDetectedType()) == false,
            null, // contentTypeConfidence
            media != null ? media.durationMs() : null,
            media != null ? media.width() : null,
            media != null ? media.height() : null,
            media != null ? media.containerFormat() : null,
            media != null ? media.primaryVideoCodec() : null,
            media != null ? media.primaryAudioCodec() : null,
            media != null && media.hasVideo(),
            media != null && media.hasAudio(),
            safeReport.detectorSummaries() != null && safeReport.detectorSummaries().stream().anyMatch(d -> d.status() == DetectorResultStatus.SUCCESS),
            safeReport.detectorSummaries() != null && safeReport.detectorSummaries().stream().anyMatch(d -> d.status() == DetectorResultStatus.SUCCESS),
            null, // detectorWarningCodes
            safeReport.policyProfile(),
            safeReport.mode() != null ? safeReport.mode().name() : null,
            policyResult != null && policyResult.decision() != null ? policyResult.decision().name() : null,
            policyResult != null && policyResult.findings() != null ? policyResult.findings().size() : 0,
            policyResult != null ? policyResult.rejectionCandidateCodes().size() : 0,
            null, // policyUserSafeMessageCodes
            null, // policyFindingCodes
            true, // uploadContinues
            false, // blocking
            null, // redactedAt
            null, // expiredAt
            null, // deletedAt
            CURRENT_SCHEMA_VERSION
        );
    }
}
