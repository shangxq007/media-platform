# Frontend Integrity Repair — App Routes

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** FRONTEND-INTEGRITY-REPAIR-APP-ROUTES.0
**Decision:** FRONTEND_INTEGRITY_APP_ROUTES_REPAIRED_WITH_LIMITS

---

## Audit Finding

P1 — Route registration gap. New frontend components existed as isolated files but were not registered in the route tree.

## Repository Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | 6c70ef2 |
| Frontend root | frontend/ |
| Router type | CODE_BASED |
| Route generation | NOT_REQUIRED |

## Route Inventory Before Repair

| Route | Component Existed | Registered | Reachable | Status |
|-------|------------------|------------|-----------|--------|
| /app/renders | RenderResultsListPage (NEW) | UserRenderHistoryPage (OLD) | YES (OLD) | MISMATCH |
| /app/renders/$productId | RenderResultDetailPage (NEW) | UserRenderResultDetailPage (OLD) | YES (OLD) | MISMATCH |
| /app/uploads | NOT_IMPLEMENTED | NOT_REGISTERED | NO | NOT_STARTED |

## Route Repairs

| File | Change |
|------|--------|
| frontend/src/app/routeTree.tsx | Replaced OLD imports with NEW contract-first components |

### Exact Changes

1. Removed: `import UserRenderHistoryPage from '../pages/UserRenderHistoryPage.js'`
2. Removed: `import UserRenderResultDetailPage from '../pages/UserRenderResultDetailPage.js'`
3. Added: `import { RenderResultsListPage } from '../routes/app/renders/RenderResultsListPage.js'`
4. Added: `import { RenderResultDetailPage } from '../routes/app/renders/RenderResultDetailPage.js'`
5. Changed: `/app/renders` component → `RenderResultsListPage`
6. Changed: `/app/renders/$productId` component → `RenderResultDetailPage`

## Route Inventory After Repair

| Route | Registered | Component | Reachable | Status |
|-------|-----------|-----------|-----------|--------|
| /app/renders | YES | RenderResultsListPage | YES | REPAIRED |
| /app/renders/$productId | YES | RenderResultDetailPage | YES | REPAIRED |
| /app/uploads | NO | NOT_IMPLEMENTED | NO | NOT_STARTED |

## Safety Boundary

| Check | Result |
|-------|--------|
| DEV imports in /app | NONE |
| Storage internals | NONE |
| Signed URL display | NONE |
| Signed URL persistence | NONE |
| New mutations | NONE |
| /app/uploads implemented | NO |

## Validation Evidence

| Command | Result |
|---------|--------|
| Drift guard (before) | PASSED (27 checks) |
| Drift guard (after) | PASSED (27 checks) |
| Typecheck | 6 errors (PRE-EXISTING, DevDiagnosticsHubPage.tsx) |
| New route-related errors | 0 |
| Lint | 1 error, 3 warnings (pre-existing in new components) |

## Upload Surface Truth

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

The canonical `/app/uploads` route remains design-only. No upload route, page, file input, navigation entry, or upload UI integration was implemented.

## Recommended Next Step

FRONTEND-INTEGRITY-REPAIR-TYPESCRIPT-BUILD.0 — Fix 6 pre-existing TypeScript errors in DevDiagnosticsHubPage.tsx
