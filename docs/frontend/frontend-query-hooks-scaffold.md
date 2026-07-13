# Frontend Query Hooks Scaffold

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-QUERY-HOOKS-SCAFFOLD.0
**Decision:** FRONTEND_QUERY_HOOKS_SCAFFOLD_READY_WITH_LIMITS

---

## Hook Structure

```
src/query/
  shared/
    query-options.ts
    polling-policy.ts
    artifact-access-policy.ts
  app/
    useProducts.ts
    useRenderJob.ts
    useArtifacts.ts
    index.ts
```

---

## Implemented Hooks

| Hook | Scope | Status |
|------|-------|--------|
| useProducts | Tenant/Project | READY |
| useProductDetail | Tenant/Project | READY |
| useRenderJobStatus | Tenant/Project | READY |
| useArtifacts | Tenant/Project | READY |
| useArtifactAccess | Tenant/Project | READY |

---

## Polling Policy

| Status | Behavior |
|--------|----------|
| QUEUED | Poll 5s |
| EXECUTING | Poll 5s |
| COMPLETED | Stop |
| FAILED | Stop |
| CANCELED | Stop |

---

## Artifact Access Policy

| Setting | Value |
|---------|-------|
| Stale time | 1 min |
| Cache time | 5 min |
| Signed URL in key | NO |
| Signed URL persisted | NO |

---

## Status

- FRONTEND-QUERY-HOOKS-SCAFFOLD.0: COMPLETE
- No mutation hooks
- No UI pages
- Safe preflight persistence: DEV_ONLY, PAUSED
