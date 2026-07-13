# Safe Preflight Report Persistence Schema Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-SCHEMA-DESIGN.0
**Decision:** SCHEMA_DESIGN_READY_WITH_LIMITS

---

## Decision

**SCHEMA_DESIGN_READY_WITH_LIMITS**

Single-table DEV_PREVIEW_EPHEMERAL_ONLY schema with typed safe columns + bounded safe code arrays.

---

## Schema Options Reviewed

| Option | Risk | Query | Stability | Recommended |
|--------|------|-------|-----------|-------------|
| A. Single table | LOW | MEDIUM | HIGH | YES |
| B. Two tables | MEDIUM | HIGH | MEDIUM | NO |
| C. Header + JSON | HIGH | MEDIUM | LOW | NO |
| D. No schema | LOW | LOW | HIGH | NO |

---

## Recommended Schema

**Table:** `ingest_preflight_safe_report_records`

### Identity/Scope Columns

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | UUID/BIGINT | NO | Primary key |
| tenant_id | VARCHAR | NO | Tenant scope |
| project_id | VARCHAR | NO | Project scope |
| raw_media_product_id | VARCHAR | NO | RAW_MEDIA reference |
| upload_attempt_id | VARCHAR | YES | Upload attempt |
| created_at | TIMESTAMP | NO | Record creation |
| expires_at | TIMESTAMP | NO | TTL expiry |
| lifecycle_state | VARCHAR | NO | RECORDED/REDACTED/EXPIRED/DELETED |
| schema_version | INT | NO | Schema version |

### Mode/Access Columns

| Column | Type | Nullable | Default | Constraint |
|--------|------|----------|---------|------------|
| persistence_mode | VARCHAR | NO | DEV_PREVIEW_EPHEMERAL_ONLY | Allowed values only |
| access_scope | VARCHAR | NO | DEV_ONLY | Allowed values only |
| retention_days | INT | NO | 7 | 1-7 |
| report_only_mode | BOOLEAN | NO | true | Always true |
| fail_open | BOOLEAN | NO | true | Always true |

### Safe Report Summary Columns

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| overall_decision | VARCHAR | NO | UploadPreflightDecision |
| warning_count | INT | NO | Warning count |
| finding_count | INT | NO | Finding count |
| reject_candidate_count | INT | NO | Diagnostic only |
| declared_mime | VARCHAR | YES | Declared MIME |
| detected_mime | VARCHAR | YES | Detected MIME |
| mime_mismatch | BOOLEAN | NO | MIME mismatch flag |
| content_type_confidence | FLOAT | YES | Confidence |
| duration_ms | BIGINT | YES | Media duration |
| width | INT | YES | Video width |
| height | INT | YES | Video height |
| container_format | VARCHAR | YES | Container |
| video_codec | VARCHAR | YES | Video codec |
| audio_codec | VARCHAR | YES | Audio codec |
| has_video | BOOLEAN | NO | Has video |
| has_audio | BOOLEAN | NO | Has audio |

### Detector Summary Columns

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| tika_detector_success | BOOLEAN | NO | Tika success |
| ffprobe_detector_success | BOOLEAN | NO | FFprobe success |
| detector_warning_codes | TEXT[]/JSON | YES | Safe code array |

### Policy Result Columns

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| policy_profile | VARCHAR | YES | Policy profile |
| policy_mode | VARCHAR | NO | REPORT_ONLY |
| policy_decision | VARCHAR | NO | Never REJECT |
| policy_finding_count | INT | NO | Finding count |
| policy_reject_candidate_count | INT | NO | Diagnostic only |
| policy_user_safe_message_codes | TEXT[]/JSON | YES | Safe codes |
| policy_finding_codes | TEXT[]/JSON | YES | Safe codes |
| upload_continues | BOOLEAN | NO | Always true |
| blocking | BOOLEAN | NO | Always false |

### Lifecycle Columns

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| redacted_at | TIMESTAMP | YES | Redaction time |
| expired_at | TIMESTAMP | YES | Expiry time |
| deleted_at | TIMESTAMP | YES | Soft delete time |

---

## Forbidden Columns

| Column | Reason |
|--------|--------|
| raw_ffprobe_json | Security |
| raw_tika_metadata | Security |
| raw_media_metadata | Security |
| raw_json | Security |
| extracted_text | Security |
| ocr_text | Security |
| local_path | Security |
| file_path | Security |
| temp_path | Security |
| upload_file_path | Security |
| bucket | Security |
| bucket_name | Security |
| object_key | Security |
| storage_reference_id | Security |
| signed_url | Security |
| presigned_url | Security |
| access_key | Security |
| secret_key | Security |
| credentials | Security |
| provider_endpoint | Security |
| provider_raw_config | Security |
| command_line | Security |
| stdout | Security |
| stderr | Security |
| stack_trace | Security |
| gps | Privacy |
| location | Privacy |
| camera | Privacy |
| device | Privacy |
| original_filename | Privacy |
| file_hash | Privacy |

---

## Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| PK | id | Primary key |
| IDX_tenant_created | tenant_id, created_at | Tenant queries |
| IDX_project_created | project_id, created_at | Project queries |
| IDX_product | raw_media_product_id | Product lookup |
| IDX_expires | expires_at | Retention cleanup |
| IDX_lifecycle | lifecycle_state | Lifecycle queries |

---

## Retention Cleanup Strategy

| Aspect | Strategy |
|--------|----------|
| TTL | expires_at = created_at + retention_days |
| Cleanup | Background job deletes WHERE expires_at < NOW() |
| Max retention | 7 days |
| Cleanup frequency | Hourly or daily |
| Soft delete | Set deleted_at, then hard delete |
| Redaction | Set redacted_at, clear sensitive fields |

---

## Lifecycle States

| State | Description |
|-------|-------------|
| RECORDED | Active record |
| EXPIRED | Past retention, pending cleanup |
| REDACTED | Fields cleared |
| DELETED | Soft deleted |

---

## Constraints

| Constraint | Rule |
|------------|------|
| blocking | MUST be false |
| upload_continues | MUST be true |
| policy_decision | MUST NOT be REJECT |
| persistence_mode | MUST be DEV_PREVIEW_EPHEMERAL_ONLY |
| access_scope | MUST be DEV_ONLY |
| retention_days | MUST be 1-7 |
| expires_at | MUST be created_at + retention_days |

---

## Required Pre-implementation Guards

1. Flyway migration must be reviewed
2. Drift guard must pass
3. Forbidden column tests must pass
4. Constraint tests must pass
5. Retention cleanup tests must pass
6. Access control tests must pass
7. Fail-open write tests must pass
8. No public response change tests must pass

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-SCHEMA-DESIGN.0: COMPLETE
- Decision: SCHEMA_DESIGN_READY_WITH_LIMITS
- Runtime persistence: NOT_IMPLEMENTED
