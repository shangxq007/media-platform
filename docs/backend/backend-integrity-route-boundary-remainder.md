# Backend Integrity — Route Boundary Remainder

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-ROUTE-BOUNDARY-REMAINDER.0
**Decision:** PRELAUNCH_ROUTE_BOUNDARY_REMAINDER_CLOSED_WITH_UNRELATED_TEST_DEBT

---

## Pre-Launch Contract Policy

The system is not launched. Internal frontend usage does not justify preserving an incorrect backend API. Current callers were migrated and obsolete routes were removed.

## Canonical RenderJob Contract

| Operation | Canonical Route |
|-----------|----------------|
| Create | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` |
| Start | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start` |
| Status/detail | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` |
| List | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` |
| Cancel | `POST /api/v1/render/jobs/{jobId}/cancel` (user-context, only implementation) |
| Retry | `POST /api/v1/render/jobs/{jobId}/retry` (user-context, only implementation) |

## Removed Routes

| Route | Handler | Reason |
|-------|---------|--------|
| `POST /render/jobs` | `create()` | Stale — canonical: `POST /tenants/{tenantId}/projects/{projectId}/render-jobs` |
| `POST /render/jobs/submit` | `submitJob()` | Stale — canonical: `POST /tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/submit` |
| `GET /render/jobs/{jobId}` | `getJob()` | Stale — canonical: `GET /tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` |
| `GET /render/jobs` | `list()` | Stale — canonical: `GET /tenants/{tenantId}/projects/{projectId}/render-jobs` |

## Retained User-Context Routes

These routes are the only implementation of their operations:

| Route | Handler | Purpose |
|-------|---------|---------|
| `POST /render/jobs/{jobId}/cancel` | `cancelJob()` | Cancel (tenantId from query param) |
| `POST /render/jobs/{jobId}/retry` | `retryJob()` | Retry (tenantId from query param) |
| `GET /render/jobs/{jobId}/status-history` | `getStatusHistory()` | Status history |
| `GET /render/jobs/{jobId}/artifacts` | `getArtifacts()` | Artifact list |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/content` | `getArtifactContent()` | Artifact content |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/access` | `getArtifactAccess()` | Artifact access |

## Frontend Migration

| File | Change |
|------|--------|
| `frontend/src/api/index.ts` | `createJob` now uses canonical tenant-scoped route with `getTenantId()` |
| `frontend/src/api/smoke-editor.ts` | Job creation now uses canonical route |
| `frontend/src/pages/DevConsolePage.tsx` | Submit now uses canonical incremental route |

## Other Caller Migration

| File | Change |
|------|--------|
| `platform-app/.../RenderFlowIntegrationTest.java` | Updated to use `createRenderJob`, `getRenderJob`, `listRenderJobs` |
| `platform-app/.../RenderNativeToolsIT.java` | Updated to canonical methods |
| `platform-app/.../RenderNatronEffectsIT.java` | Updated to canonical methods |
| `platform-app/.../RenderPipelineDagIT.java` | Updated to canonical methods |
| `platform-app/.../RenderControllerTest.java` | Rewritten to test canonical methods |
| `render-module/.../RenderControllerTest.java` | Rewritten to test canonical methods |
| `render-module/.../RenderControllerContractTest.java` | Removed legacy endpoint tests |

## No Compatibility Layer

No redirect, alias, forwarding route, fallback client, or dual-call behavior was introduced.

## Dev Mapping Reconciliation

| Mapping | Actual owner | Dev route | Registered default | Explanation |
|---------|-------------|----------|-------------------:|-------------|
| `SpaFallbackController.forwardToFrontend` | SPA routing | NO | YES | Frontend SPA fallback for `/admin/**`, `/app/**`, `/dev/**` — not a dev diagnostic |
| `DevAuthController.issueToken` | Dev auth | YES | NO (gated) | `@ConditionalOnProperty(name = "app.security.dev-auth-endpoint", havingValue = "true", matchIfMissing = false)` |

**UNEXPLAINED_DEV_RELATED_MAPPINGS: 0**

## Profile Route Matrix

| Route group | Default | Preview | Explicit dev |
|------------|--------:|--------:|------------:|
| Dev diagnostics | 0 | 0 | Registered |
| Dev auth | 0 (property off) | 0 | 0 (property off) |
| Canonical RenderJob | Registered | Registered | Registered |
| Legacy create/get/list/submit | 0 | 0 | 0 |
| User-context cancel/retry/artifacts | Registered | Registered | Registered |

## Existing Test Debt

- OIDC PostgreSQL timestamp/varchar mismatch: P2, not repaired
- FFmpeg provider registration: NOT_VERIFIED
- RenderJob lifecycle: NOT_VERIFIED

## Architecture Freeze

Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Frontend route-client correction was an integrity repair, not feature work.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.

## Recommended Next Task

**BACKEND-INTEGRITY-PROVIDER-REGISTRATION-VALIDATION.0**
