# Frontend Implementation Plan

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-IMPLEMENTATION-PLAN.0
**Decision:** FRONTEND_IMPLEMENTATION_PLAN_READY_WITH_LIMITS

---

## Context

Frontend contract gate reopened at edb7a5b. Decision: FRONTEND_CONTRACT_GATE_REOPENED_WITH_LIMITS. 2 blockers open. Safe preflight persistence remains DEV_ONLY and paused.

---

## Blocker Triage

| Blocker | Severity | Owner | Frontend Can Proceed |
|---------|----------|-------|---------------------|
| Frontend contract gate tests | P1 | SHARED | YES with fixtures |
| API snapshot tests | P2 | BACKEND | YES |

---

## Phase Plan

### Phase 0 — Contract Foundation

| Task | Goal |
|------|------|
| Frontend API client scaffold | Folder structure, shared client |
| Zod schema baseline | Schema folder, shared types |
| Contract fixtures | Backend response fixtures |
| Contract tests | Schema validation tests |
| Query key conventions | TanStack Query key rules |

**Exit Criteria:** Contract foundation ready, no UI pages.

### Phase 1 — Dev/Admin Surfaces

| Task | Goal |
|------|------|
| Dev diagnostics hub | Existing /dev routes |
| Admin render jobs | Backend contract stable |
| Storage health | Backend contract stable |

**Exit Criteria:** Dev/admin surfaces functional.

### Phase 2 — App Product Surfaces

| Task | Goal |
|------|------|
| Render result list | Backend API ready |
| Upload RAW_MEDIA | Backend API ready |
| Timeline render | Backend API ready |
| Artifact access | Backend API ready |

**Exit Criteria:** App surfaces functional, blockers resolved.

---

## Route Boundaries

| Route | Category | APIs Allowed |
|-------|----------|--------------|
| /app/* | APP | Stable product APIs only |
| /admin/* | ADMIN | Admin/ops APIs |
| /dev/* | DEV | DEV_ONLY diagnostics |

**Forbidden:**
- /app → /dev safe preflight persistence
- /app → retention dry-run
- /app → storage diagnostics

---

## API Client/Schema Plan

| Order | Contract | Status |
|-------|----------|--------|
| 1 | Shared client foundation | READY |
| 2 | Product contracts | READY |
| 3 | Render contracts | READY |
| 4 | Artifact/access contracts | READY |
| 5 | Dev diagnostics contracts | DEV_ONLY |

---

## Recommended Next Task

**FRONTEND-CONTRACT-SCHEMA-MAP.0** — 前端契约 Schema 映射

---

## Status

- FRONTEND-IMPLEMENTATION-PLAN.0: COMPLETE
- Decision: FRONTEND_IMPLEMENTATION_PLAN_READY_WITH_LIMITS
- Large UI implementation: NOT_STARTED
