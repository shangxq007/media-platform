package com.example.platform.ingest.preflight.persistence;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class SafePreflightReportRecordRepository {

    private final DSLContext dsl;

    public SafePreflightReportRecordRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    private static final String TABLE = "ingest_preflight_safe_report_records";

    public SafePreflightReportRecord save(SafePreflightReportRecord record) {
        var result = dsl.insertInto(table(TABLE))
            .columns(
                field("tenant_id"), field("project_id"), field("raw_media_product_id"),
                field("upload_attempt_id"), field("created_at"), field("expires_at"),
                field("lifecycle_state"), field("persistence_mode"), field("access_scope"),
                field("retention_days"), field("report_only_mode"), field("fail_open"),
                field("overall_decision"), field("warning_count"), field("finding_count"),
                field("reject_candidate_count"), field("declared_mime"), field("detected_mime"),
                field("mime_mismatch"), field("content_type_confidence"), field("duration_ms"),
                field("width"), field("height"), field("container_format"),
                field("video_codec"), field("audio_codec"), field("has_video"),
                field("has_audio"), field("tika_detector_success"), field("ffprobe_detector_success"),
                field("detector_warning_codes"), field("policy_profile"), field("policy_mode"),
                field("policy_decision"), field("policy_finding_count"), field("policy_reject_candidate_count"),
                field("policy_user_safe_message_codes"), field("policy_finding_codes"),
                field("upload_continues"), field("blocking"), field("schema_version")
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
            .returning(field("id"))
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
        return dsl.selectFrom(table(TABLE))
            .where(field("tenant_id").eq(tenantId))
            .and(field("project_id").eq(projectId))
            .orderBy(field("created_at").desc())
            .fetch(this::mapRecord);
    }

    public List<SafePreflightReportRecord> findByTenantProjectAndProduct(String tenantId, String projectId, String rawMediaProductId) {
        return dsl.selectFrom(table(TABLE))
            .where(field("tenant_id").eq(tenantId))
            .and(field("project_id").eq(projectId))
            .and(field("raw_media_product_id").eq(rawMediaProductId))
            .fetch(this::mapRecord);
    }

    public List<SafePreflightReportRecord> findExpired(Instant expiresAt) {
        return dsl.selectFrom(table(TABLE))
            .where(field("expires_at").lt(expiresAt))
            .fetch(this::mapRecord);
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
