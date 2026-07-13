# Safe Preflight Report Persistence Hook Integration Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-HOOK-INTEGRATION-DESIGN.0
**Decision:** HOOK_INTEGRATION_DESIGN_READY_WITH_LIMITS

---

## Context

Writer implemented. Mapper implemented. Schema implemented. Upload hook integration NOT_IMPLEMENTED.

---

## Recommended Integration Point

**Location:** Inside `UploadReportOnlyPreflightHook`

**After:**
- `SafePreflightReportSummary` is produced
- `ReportOnlyPreflightPolicyEvaluator` returns `PreflightPolicyEvaluationResult`

**Before:**
- Hook returns to normal upload flow
- RAW_MEDIA creation result is finalized

**Reason:** Safe DTOs are available, writer can be called best-effort.

---

## Context Construction

| Field | Source | Required |
|-------|--------|----------|
| tenantId | Upload context | YES |
| projectId | Upload context | YES |
| rawMediaProductId | RAW_MEDIA Product ID | YES |
| uploadAttemptId | Generated UUID if not exists | YES |
| createdAt | Instant.now() or upload timestamp | YES |

**Missing context handling:**
- Missing tenantId/projectId → SKIPPED_INVALID_INPUT
- Missing rawMediaProductId → Call after RAW_MEDIA creation, or SKIPPED_INVALID_INPUT
- Missing uploadAttemptId → Generate UUID

---

## Config Gate

**Hook-level gate:**
```java
if (writer != null && config.getMode() == DEV_PREVIEW_EPHEMERAL_ONLY) {
    // call writer best-effort
}
```

**Writer-level gate:**
- mode = DEV_PREVIEW_EPHEMERAL_ONLY
- accessScope = DEV_ONLY
- retentionDays 1-7
- failOpen = true
- publicResponseEnabled = false
- All unsafe flags = false

**Default:** mode=DISABLED → writer not called

---

## Outcome Handling

| Outcome | Hook Behavior | Log Level | Public Response | Upload Impact |
|---------|---------------|-----------|-----------------|---------------|
| SKIPPED_DISABLED | None | DEBUG | None | None |
| SKIPPED_UNSUPPORTED_MODE | None | WARN | None | None |
| SKIPPED_INVALID_INPUT | None | WARN | None | None |
| RECORDED | None | INFO | None | None |
| FAILED_OPEN | None | WARN | None | None |

---

## Failure Behavior

| Failure | Handling | Upload Impact |
|---------|----------|---------------|
| Writer exception | Caught | None |
| Repository exception | Caught | None |
| Constraint violation | Caught | None |
| Mapping exception | Caught | None |
| Invalid config | Skipped | None |
| Missing context | Skipped | None |

**Rule:** No persistence error fails upload or rolls back RAW_MEDIA.

---

## Transaction Boundary

**Recommendation:** Independent transaction inside writer

**Rejected alternatives:**
- Same transaction as upload → Risk: writer failure rolls back upload
- Async queue → Unnecessary complexity

---

## Public Response Invariance

**Forbidden public fields:**
- preflightReportId
- safePreflightReportId
- policyEvaluationId
- policyDecision
- policyFindings
- rejectCandidates
- writerOutcome
- persistenceStatus
- reportRecorded

**Required tests:**
- Response snapshot unchanged
- JSON response no persistence fields
- Upload success unchanged when RECORDED
- Upload success unchanged when FAILED_OPEN
- Upload success unchanged when SKIPPED_DISABLED

---

## Allowed Dependencies

| From | To | Allowed |
|------|----|---------|
| UploadReportOnlyPreflightHook | SafePreflightReportPersistenceWriter | YES (future) |
| UploadReportOnlyPreflightHook | SafePreflightReportRecordRepository | NO |
| UploadReportOnlyPreflightHook | SafePreflightReportRecord | NO |
| ReportOnlyPreflightPolicyEvaluator | Writer/Repository | NO |

---

## Future Tests Required

| Test | Expected |
|------|----------|
| Disabled mode upload | Writer not called, response unchanged |
| Writer success upload | Record saved, response unchanged |
| Repository failure | FAILED_OPEN, response unchanged |
| Mapper failure | Upload continues, response unchanged |
| Missing context | Skipped, upload continues |
| Public response unchanged | No persistence fields |
| No forbidden mapping | No raw metadata in record |
| Hook dependency check | Only writer abstraction |
| Policy evaluator purity | No writer/repository dependency |

---

## Drift Guard Updates Required

**Allow:**
- `UploadReportOnlyPreflightHook` imports `SafePreflightReportPersistenceWriter`

**Forbid:**
- Hook imports repository/record/jOOQ
- Policy evaluator imports writer/repository
- Public response contains persistence fields

---

## Final Recommendation

Proceed to hook integration only through a separate explicit implementation task. The implementation must remain disabled by default, fail-open, DEV_PREVIEW_EPHEMERAL_ONLY only, and public-response-neutral.

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-HOOK-INTEGRATION-DESIGN.0: COMPLETE
- Decision: HOOK_INTEGRATION_DESIGN_READY_WITH_LIMITS
- Upload hook integration: NOT_IMPLEMENTED
