# Architecture Drift Guard

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** ARCH-DRIFT-GUARD.0

---

## Purpose

LikeC4 is human-maintained architecture intent. Drift guard checks critical code/docs facts. It is not a full static analysis system.

---

## What It Checks

| Category | Checks |
|----------|--------|
| Required Classes | 9 classes exist |
| Runtime Profile Switching | No resolver in production |
| Storage Exposure | No credentials in DTOs |
| Report-only Evaluator | No REJECT emitted |
| Upload Rejection | No PreflightRejected |
| Persistence | No policy repositories |
| Deferred Status | OpenCue/Artifact DAG status |

---

## How to Run

```bash
bash scripts/check-architecture-drift.sh
```

---

## What It Does Not Check

- Full dependency graph
- Every code path
- Generated diagrams
- Semantic correctness of LikeC4 views

---

## Change Process

1. Update assertion doc
2. Update LikeC4
3. Update script
4. Update current-system-state/module-status
5. Run guard
6. Record commit

---

## Status

- ARCH-DRIFT-GUARD.0: COMPLETE
- All checks: PASSED
