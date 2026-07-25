package com.example.platform.ingest.preflight.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.IngestPreflightSafeReportRecords.INGEST_PREFLIGHT_SAFE_REPORT_RECORDS;


@Repository
public class SafePreflightReportRecordRepository {

    private final DSLContext dsl;

    public SafePreflightReportRecordRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    private static final String TABLE = "ingest_preflight_safe_report_records";

    public SafePreflightReportRecord save(SafePreflightReportRecord record) {
        var result = dsl.insertInto(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS)
            .columns(
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.TENANT_ID, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.PROJECT_ID, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.RAW_MEDIA_PRODUCT_ID,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.UPLOAD_ATTEMPT_ID, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.CREATED_AT, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.EXPIRES_AT,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.LIFECYCLE_STATE, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.PERSISTENCE_MODE, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.ACCESS_SCOPE,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.RETENTION_DAYS, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.REPORT_ONLY_MODE, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.FAIL_OPEN,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.OVERALL_DECISION, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.WARNING_COUNT, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.FINDING_COUNT,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.REJECT_CANDIDATE_COUNT, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.DECLARED_MIME, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.DETECTED_MIME,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.MIME_MISMATCH, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.CONTENT_TYPE_CONFIDENCE, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.DURATION_MS,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.WIDTH, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.HEIGHT, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.CONTAINER_FORMAT,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.VIDEO_CODEC, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.AUDIO_CODEC, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.HAS_VIDEO,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.HAS_AUDIO, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.TIKA_DETECTOR_SUCCESS, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.FFPROBE_DETECTOR_SUCCESS,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.DETECTOR_WARNING_CODES, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_PROFILE, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_MODE,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_DECISION, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_FINDING_COUNT, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_REJECT_CANDIDATE_COUNT,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_USER_SAFE_MESSAGE_CODES, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.POLICY_FINDING_CODES,
                INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.UPLOAD_CONTINUES, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.BLOCKING, INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.SCHEMA_VERSION
            )
            .values(
                record.tenantId(), record.projectId(), record.rawMediaProductId(),
                record.uploadAttemptId(), record.createdAt(), record.expiresAt(),
                record.lifecycleState(), record.persistenceMode(), record.accessScope(),
                record.retentionDays(), record.reportOnlyMode(), record.failOpen(),
                record.overallDecision(), record.warningCount(), record.findingCount(),
                record.rejectCandidateCount(), record.declaredMime(), record.detectedMime(),
                record.mimeMismatch(), record.contentTypeConfidence(), record.durationMs(),
                record.width(), record.height(), record.containerFormat(),
                record.videoCodec(), record.audioCodec(), record.hasVideo(),
                record.hasAudio(), record.tikaDetectorSuccess(), record.ffprobeDetectorSuccess(),
                record.detectorWarningCodes(), record.policyProfile(), record.policyMode(),
                record.policyDecision(), record.policyFindingCount(), record.policyRejectCandidateCount(),
                record.policyUserSafeMessageCodes(), record.policyFindingCodes(),
                record.uploadContinues(), record.blocking(), record.schemaVersion()
            )
            .returning(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.ID)
            .fetchOne();

        Long id = result != null ? result.get("id", Long.class) : null;
        return new SafePreflightReportRecord(
            id, record.tenantId(), record.projectId(), record.rawMediaProductId(),
            record.uploadAttemptId(), record.createdAt(), record.expiresAt(),
            record.lifecycleState(), record.persistenceMode(), record.accessScope(),
            record.retentionDays(), record.reportOnlyMode(), record.failOpen(),
            record.overallDecision(), record.warningCount(), record.findingCount(),
            record.rejectCandidateCount(), record.declaredMime(), record.detectedMime(),
            record.mimeMismatch(), record.contentTypeConfidence(), record.durationMs(),
            record.width(), record.height(), record.containerFormat(),
            record.videoCodec(), record.audioCodec(), record.hasVideo(),
            record.hasAudio(), record.tikaDetectorSuccess(), record.ffprobeDetectorSuccess(),
            record.detectorWarningCodes(), record.policyProfile(), record.policyMode(),
            record.policyDecision(), record.policyFindingCount(), record.policyRejectCandidateCount(),
            record.policyUserSafeMessageCodes(), record.policyFindingCodes(),
            record.uploadContinues(), record.blocking(),
            record.redactedAt(), record.expiredAt(), record.deletedAt(), record.schemaVersion()
        );
    }

    public List<SafePreflightReportRecord> findByTenantAndProject(String tenantId, String projectId) {
        return dsl.selectFrom(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS)
            .where(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.TENANT_ID.eq(tenantId))
            .and(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.PROJECT_ID.eq(projectId))
            .orderBy(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.CREATED_AT.desc())
            .fetch(this::mapRecord);
    }

    public List<SafePreflightReportRecord> findByTenantProjectAndProduct(String tenantId, String projectId, String rawMediaProductId) {
        return dsl.selectFrom(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS)
            .where(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.TENANT_ID.eq(tenantId))
            .and(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.PROJECT_ID.eq(projectId))
            .and(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.RAW_MEDIA_PRODUCT_ID.eq(rawMediaProductId))
            .fetch(this::mapRecord);
    }

    public List<SafePreflightReportRecord> findExpired(Instant expiresAt) {
        return dsl.selectFrom(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS)
            .where(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.EXPIRES_AT.lt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)))
            .fetch(this::mapRecord);
    }

    public Optional<SafePreflightReportRecord> findByIdAndTenantProject(Long id, String tenantId, String projectId) {
        return dsl.selectFrom(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS)
            .where(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.ID.eq(id))
            .and(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.TENANT_ID.eq(tenantId))
            .and(INGEST_PREFLIGHT_SAFE_REPORT_RECORDS.PROJECT_ID.eq(projectId))
            .fetchOptional(this::mapRecord);
    }

    private SafePreflightReportRecord mapRecord(Record r) {
        return new SafePreflightReportRecord(
            r.get("id", Long.class),
            r.get("tenant_id", String.class),
            r.get("project_id", String.class),
            r.get("raw_media_product_id", String.class),
            r.get("upload_attempt_id", String.class),
            r.get("created_at", Instant.class),
            r.get("expires_at", Instant.class),
            r.get("lifecycle_state", String.class),
            r.get("persistence_mode", String.class),
            r.get("access_scope", String.class),
            r.get("retention_days", Integer.class),
            r.get("report_only_mode", Boolean.class),
            r.get("fail_open", Boolean.class),
            r.get("overall_decision", String.class),
            r.get("warning_count", Integer.class),
            r.get("finding_count", Integer.class),
            r.get("reject_candidate_count", Integer.class),
            r.get("declared_mime", String.class),
            r.get("detected_mime", String.class),
            r.get("mime_mismatch", Boolean.class),
            r.get("content_type_confidence", Double.class),
            r.get("duration_ms", Long.class),
            r.get("width", Integer.class),
            r.get("height", Integer.class),
            r.get("container_format", String.class),
            r.get("video_codec", String.class),
            r.get("audio_codec", String.class),
            r.get("has_video", Boolean.class),
            r.get("has_audio", Boolean.class),
            r.get("tika_detector_success", Boolean.class),
            r.get("ffprobe_detector_success", Boolean.class),
            r.get("detector_warning_codes", String.class),
            r.get("policy_profile", String.class),
            r.get("policy_mode", String.class),
            r.get("policy_decision", String.class),
            r.get("policy_finding_count", Integer.class),
            r.get("policy_reject_candidate_count", Integer.class),
            r.get("policy_user_safe_message_codes", String.class),
            r.get("policy_finding_codes", String.class),
            r.get("upload_continues", Boolean.class),
            r.get("blocking", Boolean.class),
            r.get("redacted_at", Instant.class),
            r.get("expired_at", Instant.class),
            r.get("deleted_at", Instant.class),
            r.get("schema_version", Integer.class)
        );
    }
}
