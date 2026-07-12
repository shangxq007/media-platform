# Render Worker Claiming Hardening

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-CLAIMING-HARDENING.0

---

## Claim State Machine

| Transition | Condition |
|------------|-----------|
| QUEUED → EXECUTING | Atomic claim (WHERE status = 'QUEUED') |
| EXECUTING → COMPLETED | Output Product/Artifact verified |
| EXECUTING → FAILED | Safe failure |
| Stale EXECUTING → FAILED | Recovery (default) |
| Stale EXECUTING → QUEUED | Recovery (requeue) |

---

## Atomic Claim

**Algorithm:**
```sql
UPDATE render_job
SET status = 'EXECUTING', updated_at = now()
WHERE id = ? AND status = 'QUEUED'
```

**Properties:**
- Atomic: YES (single UPDATE with WHERE clause)
- Multi-worker safe: YES (second claim returns 0 rows updated)
- Idempotent: YES (re-claim returns 0 if already EXECUTING)

---

## Claim Metadata

| Field | Status |
|-------|--------|
| workerId | PARTIAL (parameter accepted, not persisted to column) |
| claimedAt | Via updated_at |
| attemptCount | DEFERRED |
| claimToken | DEFERRED |

---

## Completion/Failure Guards

| Guard | Status |
|-------|--------|
| Only EXECUTING → COMPLETED | ✅ |
| Only EXECUTING → FAILED | ✅ |
| WorkerId guard | DEFERRED |

---

## Requeue Semantics

| Property | Value |
|----------|-------|
| Default action | FAIL |
| Requeue action | DEV/PREVIEW ONLY |
| Max requeue attempts | DEFERRED |

---

## Multi-worker Race Test

| Test | Result |
|------|--------|
| Two concurrent claims | ✅ Only one succeeds |
| Second claim returns 0 | ✅ |
| No duplicate execution | ✅ |

---

## Status

- RENDER-WORKER-CLAIMING-HARDENING.0: COMPLETE
- Atomic claim: VERIFIED
- Multi-worker safe: BASIC
