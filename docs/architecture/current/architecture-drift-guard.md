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

---

## CI Enforcement

Architecture drift guard runs in CI using:
```bash
bash scripts/check-architecture-drift.sh
```

**Workflow:** `.github/workflows/architecture-drift.yml`

**Triggers:**
- Pull requests
- Pushes to main

**What a failure means:**
A status-critical architecture assertion may have drifted. It does not necessarily mean the change is wrong.

**How to fix intentional changes:**
1. Update implementation
2. Update LikeC4
3. Update architecture assertions
4. Update drift guard script
5. Update current-system-state/module-status
6. Update docs for affected subsystem

**Local reproduction:**
```bash
bash scripts/check-architecture-drift.sh
```

---

## Safe Preflight Report Persistence Guard

**Checks:**
- No persistence writer/repository classes
- No Flyway migration for preflight
- No JPA entity for preflight/policy
- Enforce mode not enabled
- Report-only evaluator does not emit REJECT

**Allowed:**
- Persistence classes in docs as future-only
- Enforce mode in enum definitions as future-only

**Forbidden in production:**
- SafePreflightReportPersistenceWriter
- PreflightReportRepository
- V*__*preflight*.sql
- @Entity on preflight classes
