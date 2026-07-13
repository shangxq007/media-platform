# Frontend Query Key Conventions

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-QUERY-KEY-CONVENTIONS.0

---

## Query Key Pattern

All query keys follow: `[domain, ...scope, ...params]`

Tenant/Project scoped resources include `tenantId` and `projectId`.

DEV_ONLY keys are prefixed with `dev` and isolated.

---

## Query Key Table

| Resource | Key Pattern | Scope |
|----------|-------------|-------|
| Products list | `['products', tenantId, projectId]` | Tenant/Project |
| Product detail | `['products', tenantId, projectId, productId]` | Tenant/Project |
| Render jobs | `['renderJobs', tenantId, projectId]` | Tenant/Project |
| Artifacts | `['artifacts', tenantId, projectId, jobId]` | Tenant/Project |
| Artifact access | `['artifactAccess', tenantId, projectId, jobId, artifactId]` | Tenant/Project |
| Dev storage profiles | `['dev', 'storageDeliveryProfiles']` | Global |
| Dev safe reports | `['dev', 'safePreflightReports', tenantId, projectId]` | Tenant/Project |

---

## Cache Boundaries

| Resource | Stale Time | Cache Time |
|----------|------------|------------|
| Products | 5 min | 10 min |
| Render Jobs | 30 sec | 2 min |
| Artifacts | 5 min | 10 min |
| Artifact Access | 1 min | 5 min |
| Dev diagnostics | 5 min | 10 min |

---

## Polling Intervals

| Resource | Interval |
|----------|----------|
| Render job status | 5 sec |
| Timeline revision render | 3 sec |

---

## Status

- FRONTEND-QUERY-KEY-CONVENTIONS.0: COMPLETE
- No UI pages implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
