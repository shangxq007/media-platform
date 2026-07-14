# Backend Integrity — RenderJob Route Contract Finalization

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-RENDERJOB-ROUTE-CONTRACT-FINALIZATION.0
**Decision:** RENDERJOB_ROUTE_CONTRACT_FINALIZED_WITH_UNRELATED_TEST_DEBT

---

## Pre-Launch Policy

The system is not launched. Being the only implementation was not treated as proof that a route was architecturally valid. Unsupported or lifecycle-invalid public routes were removed rather than preserved for compatibility.

## Final Canonical RenderJob API

| Operation | Route | Status |
|-----------|-------|--------|
| Create | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | CANONICAL |
| Start | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start` | CANONICAL |
| Status/detail | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` | CANONICAL |
| List | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | CANONICAL |
| Cancel | `POST /api/v1/render/jobs/{jobId}/cancel` | CANONICAL |
| Status history | `GET /api/v1/render/jobs/{jobId}/status-history` | CANONICAL |
| Artifact list | `GET /api/v1/render/jobs/{jobId}/artifacts` | CANONICAL |
| Artifact content | `GET /api/v1/render/jobs/{jobId}/artifacts/{artifactId}/content` | CANONICAL |
| Artifact access | `GET /api/v1/render/jobs/{jobId}/artifacts/{artifactId}/access` | CANONICAL |

## Removed Routes

| Route | Handler | Reason |
|-------|---------|--------|
| `POST /render/jobs/{jobId}/retry` | `retryJob()` | **LIFECYCLE_SEMANTICS_INVALID** — reuses same RenderJob, violates one-attempt rule |

## Retry and One-Attempt Rule

The `retry` method in `RenderJobService` resets the same RenderJob to QUEUED status. This violates the architecture rule that "RenderJob represents one execution attempt." A valid retry would create a NEW RenderJob with provenance linking to the previous attempt. Since no such implementation exists, the public route was removed.

## Artifact Resource Ownership

The 3 artifact-related routes (`artifacts`, `artifacts/{id}/content`, `artifacts/{id}/access`) are **RenderJob-scoped queries** — they verify the artifact belongs to the requested job before returning content. This is different from a direct Artifact API access. These are valid as job-scoped operations.

## Route Disposition Matrix

| Route | Classification | Disposition | Reason |
|-------|---------------|-------------|--------|
| `POST /render/jobs/{jobId}/cancel` | CANONICAL_RENDERJOB_OPERATION | KEEP_CANONICAL | Valid lifecycle transition to CANCELLED |
| `POST /render/jobs/{jobId}/retry` | LIFECYCLE_SEMANTICS_INVALID | REMOVE_NOW | Reuses same RenderJob, violates one-attempt rule |
| `GET /render/jobs/{jobId}/status-history` | CANONICAL_RENDERJOB_OPERATION | KEEP_CANONICAL | Valid execution audit query |
| `GET /render/jobs/{jobId}/artifacts` | CANONICAL_RENDERJOB_OPERATION | KEEP_CANONICAL | Valid job-to-artifact relationship query |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/content` | CANONICAL_RENDERJOB_OPERATION | KEEP_CANONICAL | Valid job-scoped artifact content access |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/access` | CANONICAL_RENDERJOB_OPERATION | KEEP_CANONICAL | Valid job-scoped artifact access descriptor |

## Caller Migration

| File | Change |
|------|--------|
| `frontend/src/api/render-jobs.ts` | Removed `retry()` function and `useRetryRenderJob` hook |
| `frontend/src/api/admin/render.ts` | Removed `retryJob()` function |
| `frontend/src/components/render-jobs/JobDetail.tsx` | Removed retry button |
| `platform-app/.../RenderControllerTest.java` | Removed retry test |
| `render-module/.../RenderControllerContractTest.java` | Removed retry test |

## No Compatibility Layer

No alias, redirect, forwarding mapping, fallback request, or dual-call behavior was introduced.

## Existing Test Debt

- OIDC PostgreSQL timestamp/varchar mismatch: P2, not repaired
- FFmpeg provider registration: NOT_VERIFIED
- RenderJob lifecycle: NOT_VERIFIED

## Architecture Freeze

Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.

## Recommended Next Task

**BACKEND-INTEGRITY-PROVIDER-REGISTRATION-VALIDATION.0**
