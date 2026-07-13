# Safe Preflight Report Persistence Writer Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-WRITER-DESIGN.0
**Decision:** WRITER_DESIGN_READY_WITH_LIMITS

---

## Context

Schema implemented. Repository exists. Runtime writer NOT_IMPLEMENTED. Config default DISABLED.

---

## Writer Responsibilities

**Future component:** `SafePreflightReportPersistenceWriter`

| Responsibility | Description |
|----------------|-------------|
| Accept safe DTOs | SafePreflightReportSummary + PreflightPolicyEvaluationResult |
| Accept context | tenantId, projectId, rawMediaProductId, uploadAttemptId |
| Validate config | Mode, access, retention, fail-open, flags |
| Map safe fields | Only approved fields, no raw metadata |
| Compute expiresAt | createdAt + retentionDays |
| Set fixed fields | persistenceMode, accessScope, lifecycleState |
| Call repository | Fail-open, independent transaction |
| Return outcome | SKIPPED_* or RECORDED or FAILED_OPEN |

**Non-responsibilities:**
- Run Tika/FFprobe
- Evaluate policy
- Select storage provider
- Generate signed URLs
- Mutate Product/RAW_MEDIA
- Change upload response
- Enforce policy
- Reject upload

---

## Interface Design

```java
public interface SafePreflightReportPersistenceWriter {
    SafePreflightPersistenceWriteOutcome writeReportOnlySafeRecord(
        SafePreflightPersistenceContext context,
        SafePreflightReportSummary safeReport,
        PreflightPolicyEvaluationResult policyResult
    );
}
```

**Outcome enum:**
- `SKIPPED_DISABLED` — Mode disabled
- `SKIPPED_UNSUPPORTED_MODE` — Mode not supported
- `SKIPPED_INVALID_INPUT` — Invalid data
- `RECORDED` — Successfully saved
- `FAILED_OPEN` — Failed, upload continues

---

## Config Gate

Writer writes only if ALL are true:

| Condition | Required Value |
|-----------|---------------|
| mode | DEV_PREVIEW_EPHEMERAL_ONLY |
| accessScope | DEV_ONLY |
| retentionDays | 1-7 |
| failOpen | true |
| publicResponseEnabled | false |
| allowRawMetadata | false |
| allowLocalPath | false |
| allowStorageInternals | false |
| allowSignedUrl | false |
| allowCredentials | false |

**Default:** mode=DISABLED → writer does nothing

---

## Safe Mapping Rules

| Source | Target | Notes |
|--------|--------|-------|
| context.tenantId | tenant_id | Required |
| context.projectId | project_id | Required |
| context.rawMediaProductId | raw_media_product_id | Required |
| context.uploadAttemptId | upload_attempt_id | Optional |
| context.createdAt | created_at | Required |
| createdAt + retentionDays | expires_at | Computed |
| — | persistence_mode | DEV_PREVIEW_EPHEMERAL_ONLY |
| — | access_scope | DEV_ONLY |
| — | lifecycle_state | RECORDED |
| — | report_only_mode | true |
| — | fail_open | true |
| — | upload_continues | true |
| — | blocking | false |
| safeReport.overallDecision | overall_decision | |
| safeReport.warningCount | warning_count | |
| safeReport.findingCount | finding_count | |
| safeReport.rejectCandidateCount | reject_candidate_count | |
| safeReport.declaredMime | declared_mime | |
| safeReport.detectedMime | detected_mime | |
| safeReport.mimeMismatch | mime_mismatch | |
| policyResult.decision | policy_decision | Must not be REJECT |
| policyResult.findingCount | policy_finding_count | |

---

## Forbidden Mapping

**Never read or map:**
- raw FFprobe JSON, raw Tika metadata, raw media metadata
- extracted text, OCR text
- original filename, file hash
- local path, file path, temp path
- bucket, objectKey, storageReferenceId
- signed URL, presigned URL
- accessKey, secretKey, credentials
- provider endpoint, provider raw config
- command line, stdout, stderr, stack trace
- GPS/location/device/camera metadata

---

## Transaction Boundary

**Recommendation:** Independent best-effort transaction

| Option | Risk | Recommended |
|--------|------|-------------|
| Same transaction as upload | HIGH | NO |
| Independent transaction | LOW | YES |
| Best-effort async | MEDIUM | NO |

**Reason:** Upload must not roll back on persistence failure.

---

## Failure Behavior

| Failure | Outcome | Upload Impact |
|---------|---------|---------------|
| Config invalid | SKIPPED_INVALID_INPUT | None |
| Repository failure | FAILED_OPEN | None |
| Constraint violation | FAILED_OPEN | None |
| Mapping failure | FAILED_OPEN | None |
| policyDecision=REJECT | SKIPPED_INVALID_INPUT | None |
| Missing context | SKIPPED_INVALID_INPUT | None |

**Rule:** No exception escapes writer into upload path.

---

## Upload Hook Integration Point

**Location:** After SafePreflightReportSummary + PreflightPolicyEvaluationResult produced

**Rules:**
- Call writer best-effort if enabled
- Don't change hook return value
- Don't add writer outcome to public response
- Don't block upload on writer failure
- Don't emit REJECT

---

## Future Tests Required

| Test | Expected |
|------|----------|
| Disabled mode | Writer skips, repo not called |
| Dev-preview mode | Writer maps and calls repo |
| Invalid config fail-open | No write, upload continues |
| Repository failure fail-open | FAILED_OPEN, upload continues |
| Forbidden field mapping | No forbidden fields in record |
| REJECT decision | Skip/FAILED_OPEN, upload continues |
| Upload hook integration | Hook result unchanged |
| Public response unchanged | No report ID in response |
| Transaction failure | No rollback of RAW_MEDIA |

---

## Drift Guard Updates Required

Before implementation, allow:
- `SafePreflightReportPersistenceWriter`
- `SafePreflightPersistenceWriteOutcome`

Still forbid:
- Raw metadata fields
- Local path fields
- Storage internal fields
- Signed URL fields
- Credential fields
- Public upload response fields
- Enforce mode
- Upload rejection

---

## Final Recommendation

Proceed to writer implementation only through a separate explicit task, keeping runtime disabled by default and fail-open.

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-WRITER-DESIGN.0: COMPLETE
- Decision: WRITER_DESIGN_READY_WITH_LIMITS
- Runtime writer: NOT_IMPLEMENTED
