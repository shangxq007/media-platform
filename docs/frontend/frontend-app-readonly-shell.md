# Frontend App Readonly Shell

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-READONLY-SHELL.0
**Decision:** FRONTEND_APP_READONLY_SHELL_READY_WITH_LIMITS

---

## Shell Structure

```
src/routes/app/
  AppShell.tsx
  renders/
    RenderResultsListPage.tsx
    RenderResultDetailPage.tsx
```

---

## Routes

| Route | Component | Status |
|-------|-----------|--------|
| /app | AppShell | READY |
| /app/renders | RenderResultsListPage | READY_WITH_LIMITS |
| /app/renders/$productId | RenderResultDetailPage | READY_WITH_LIMITS |

---

## Safety

- No mutations
- No DEV exposure
- No storage internals
- No signed URL as canonical metadata

---

## Status

- FRONTEND-APP-READONLY-SHELL.0: COMPLETE
- No production data fetching wired
- Safe preflight persistence: DEV_ONLY, PAUSED
