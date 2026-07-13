# Frontend Contract Gate Reopen

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-CONTRACT-GATE-REOPEN.0
**Decision:** FRONTEND_CONTRACT_GATE_REOPENED_WITH_LIMITS

---

## Context

Backend API audit complete. API_CONTRACT_PARTIAL_NEEDS_FIXES. Frontend readiness: FRONTEND_CONTRACT_GATE_READY_WITH_LIMITS.

---

## Contract Surfaces

### Stable APIs (Frontend May Depend)

| Surface | Endpoint | Status |
|---------|----------|--------|
| User dashboard | /api/v1/me/dashboard | STABLE |
| User projects | /api/v1/me/projects | STABLE |
| Billing | /api/v1/billing/me/* | STABLE |
| Health | /healthz, /readyz | STABLE |

### DEV Only (Must Not Expose in /app)

| Surface | Endpoint | Status |
|---------|----------|--------|
| Storage diagnostics | /dev/storage-delivery-profiles | DEV_ONLY |
| Ingest diagnostics | /dev/ingest/preflight-policy | DEV_ONLY |
| Preflight reports | /dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports | DEV_ONLY |
| Retention dry-run | /dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports/retention/dry-run | DEV_ONLY |

---

## TypeScript/Zod Contract Rules

| Rule | Description |
|------|-------------|
| Backend DTOs are source of truth | TypeScript types must match backend |
| Zod schemas validate API responses | Runtime validation at API boundary |
| DEV_ONLY DTOs must not leak to /app | Separate type exports |
| No raw metadata in frontend types | Forbidden fields blocked |

---

## Blockers

| Blocker | Severity | Status |
|---------|----------|--------|
| Frontend contract gate tests | MEDIUM | OPEN |
| API snapshot tests | LOW | OPEN |

---

## Status

- FRONTEND-CONTRACT-GATE-REOPEN.0: COMPLETE
- Decision: FRONTEND_CONTRACT_GATE_REOPENED_WITH_LIMITS
- Safe preflight persistence: PAUSED, DEV_ONLY
