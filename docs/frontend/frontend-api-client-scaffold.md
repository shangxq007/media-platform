# Frontend API Client Scaffold

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-API-CLIENT-SCAFFOLD.0
**Decision:** FRONTEND_API_CLIENT_SCAFFOLD_READY_WITH_LIMITS

---

## Client Structure

```
src/api/
  core/
    api-client.ts
    endpoint-builder.ts
  app/
    products.client.ts
    artifacts.client.ts
    index.ts
  dev/
    safe-preflight-reports.client.ts
    index.ts
  index.ts (app only)
```

---

## Core Client

- Shared fetch wrapper
- Zod response parsing
- ApiResult<T> / ApiError types
- Endpoint path builders

---

## App Client Boundary

- Products client
- Artifacts client
- Does NOT call /dev endpoints
- Does NOT import dev contracts

---

## Dev Client Boundary

- Safe preflight reports client (DEV_ONLY)
- Retention dry-run client (DEV_ONLY)
- Isolated from app barrel

---

## Status

- FRONTEND-API-CLIENT-SCAFFOLD.0: COMPLETE
- No UI pages implemented
- No TanStack Query hooks added
- Safe preflight persistence: DEV_ONLY, PAUSED
