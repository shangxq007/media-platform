# Frontend Contract Schema Map

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-CONTRACT-SCHEMA-MAP.0
**Decision:** FRONTEND_SCHEMA_MAP_READY_WITH_LIMITS

---

## Contract Directory Structure

```
src/contracts/
  shared/
    primitives.ts
  app/
    product.ts
    upload.ts
    render-job.ts
    artifact.ts
    index.ts
  dev/
    safe-preflight-report.ts
  index.ts (app only)
```

---

## Ownership Rules

- Backend DTOs are source of truth
- Zod schemas validate client boundary
- DEV_ONLY schemas isolated from app
- Forbidden fields blocked in app contracts

---

## Status

- FRONTEND-CONTRACT-SCHEMA-MAP.0: COMPLETE
- No UI pages implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
