# Safe Preflight Report Persistence Retention Dry-run Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-RETENTION-DRY-RUN-DESIGN.0
**Decision:** DRY_RUN_DESIGN_READY_WITH_LIMITS

---

## Context

Retention design complete. Cleanup NOT_IMPLEMENTED. Dry-run NOT_IMPLEMENTED.

---

## Dry-run Purpose

**Answers:**
- How many records are eligible for cleanup
- Which cleanup strategy would apply
- What batch size would be used
- Whether safety checks pass

**Never does:**
- Delete records
- Mark records expired
- Update lifecycle state
- Call upload hook/writer/storage

---

## Endpoint Shape

| Endpoint | Method | Scope |
|----------|--------|-------|
| `/dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports/retention/dry-run` | GET | Tenant/project |

---

## Request Parameters

| Parameter | Default | Required |
|-----------|---------|----------|
| mode | DEV_PREVIEW_EPHEMERAL_ONLY | YES |
| accessScope | DEV_ONLY | YES |
| batchLimit | 100 | NO |
| now | Server clock | NO |

---

## Response DTO

| Field | Type | Description |
|-------|------|-------------|
| tenantId | String | Tenant scope |
| projectId | String | Project scope |
| mode | String | Persistence mode |
| accessScope | String | Access scope |
| strategy | String | Cleanup strategy |
| now | Instant | Evaluation time |
| retentionDaysMax | Int | Max retention |
| batchLimit | Int | Configured batch |
| eligibleExpiredCount | Long | Total eligible |
| wouldProcessCount | Int | Would process |
| wouldDeleteCount | Int | Would delete |
| oldestExpiredAt | Instant | Oldest expired |
| newestExpiredAt | Instant | Newest expired |
| safetyChecksPassed | Boolean | All checks passed |
| outcome | String | DRY_RUN_COMPLETE |

---

## Safety Checks

| Check | Required |
|-------|----------|
| modeIsDevPreviewEphemeralOnly | YES |
| accessScopeIsDevOnly | YES |
| retentionDaysMaxWithinLimit | YES |
| batchLimitWithinLimit | YES |
| tenantProjectScopePresent | YES |
| expiresAtPredicateRequired | YES |
| noProductArtifactStorageDeletion | YES |
| noFileDeletion | YES |
| noStorageObjectDeletion | YES |
| noMutationInDryRun | YES |

---

## Eligibility

**Eligible if ALL true:**
- persistenceMode = DEV_PREVIEW_EPHEMERAL_ONLY
- accessScope = DEV_ONLY
- expiresAt < NOW()
- retentionDays <= 7
- lifecycleState = RECORDED or EXPIRED
- deletedAt IS NULL

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-RETENTION-DRY-RUN-DESIGN.0: COMPLETE
- Decision: DRY_RUN_DESIGN_READY_WITH_LIMITS
- Dry-run runtime: NOT_IMPLEMENTED
