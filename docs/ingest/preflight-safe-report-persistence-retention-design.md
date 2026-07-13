# Safe Preflight Report Persistence Retention Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-RETENTION-DESIGN.0
**Decision:** RETENTION_DESIGN_READY_WITH_LIMITS

---

## Context

Schema implemented. Writer integrated. Read endpoints implemented. Cleanup NOT_IMPLEMENTED.

---

## Retention Semantics

| Rule | Value |
|------|-------|
| Max retention | 7 days |
| Mode | DEV_PREVIEW_EPHEMERAL_ONLY |
| Access | DEV_ONLY |
| Expiration | expiresAt = createdAt + retentionDays |
| Scope | Safe preflight records only |

---

## Lifecycle States

| State | Description | Read Visible | Cleanup Eligible |
|-------|-------------|--------------|------------------|
| RECORDED | Active | YES | NO |
| EXPIRED | Past retention | NO (default) | YES |
| REDACTED | Fields cleared | Safe only | YES |
| DELETED | Soft deleted | NO | NO |

---

## Cleanup Eligibility

**Eligible if ALL true:**
- persistenceMode = DEV_PREVIEW_EPHEMERAL_ONLY
- accessScope = DEV_ONLY
- expiresAt < NOW()
- retentionDays <= 7
- lifecycleState = RECORDED or EXPIRED
- deletedAt IS NULL

**Not eligible if ANY true:**
- Wrong mode/access
- expiresAt IS NULL
- expiresAt >= NOW()
- retentionDays > 7
- lifecycleState = DELETED

---

## Recommended Staged Strategy

| Stage | Description | Status |
|-------|-------------|--------|
| 1 | Read-time expiration | ✅ CURRENT |
| 2 | DEV_ONLY dry-run diagnostics | FUTURE |
| 3 | Bounded cleanup implementation | FUTURE |
| 4 | Scheduler | FUTURE |

---

## Safe Failure Behavior

| Failure | Outcome |
|---------|---------|
| Repository query | FAILED_SAFE |
| Repository delete | FAILED_SAFE |
| Partial batch | PARTIAL_SAFE |
| Invalid config | SKIPPED_INVALID_CONFIG |
| Mode disabled | SKIPPED_DISABLED |

---

## Batch Limits

| Setting | Value |
|---------|-------|
| Default batch | 100 |
| Max batch | 1000 |
| Sort | expiresAt ASC |
| One batch per invocation | YES |

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-RETENTION-DESIGN.0: COMPLETE
- Decision: RETENTION_DESIGN_READY_WITH_LIMITS
- Cleanup runtime: NOT_IMPLEMENTED
