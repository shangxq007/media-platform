# Backend Integrity — Pre-Launch Route Boundary Correction

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-ROUTE-BOUNDARY-CORRECTION.0
**Decision:** PRELAUNCH_ROUTE_BOUNDARIES_CORRECTED_WITH_REMAINING_TEST_DEBT

---

## Pre-Launch Policy

The system is not yet launched. Unapproved compatibility debt is not preserved by default. Proven route and boundary design errors are corrected before launch.

## Baseline

| Item | Value |
|------|-------|
| Base commit | 52c945f |
| Application module | platform-app |
| Profiles | test, preview |
| Pre-change dev mappings | 11 |
| Pre-change execute-local | ACTIVE |
| Pre-change stale legacy routes | 7 (frontend consumer — retained) |

## Dev Route Findings

| Controller | Route | Gating Before | Gating After |
|-----------|-------|--------------|-------------|
| DevAuthController | `/api/v1/dev/auth/token` | `@ConditionalOnProperty` (default-off) | No change needed |
| DevStorageDeliveryProfileDiagnosticsController | `/dev/storage-delivery-profiles/**` | NONE | `@Profile("dev")` |
| DevIngestPreflightPolicyDiagnosticsController | `/dev/ingest/preflight-policy/**` | NONE | `@Profile("dev")` |
| DevSafePreflightReportReadController | `/dev/tenants/.../safe-reports/**` | NONE | `@Profile("dev")` |
| DevSafePreflightReportRetentionDryRunController | `/dev/tenants/.../retention/**` | NONE | `@Profile("dev")` |

## Dev Isolation Correction

- Mechanism: `@Profile("dev")` on 4 Controllers
- Default behavior: dev routes absent (404)
- Preview behavior: dev routes absent (404)
- Production-like behavior: dev routes absent (404)
- Explicit dev profile: dev routes registered

## Canonical RenderJob Contract

| Operation | Canonical Route | Legacy Route (retained) |
|-----------|----------------|------------------------|
| Create | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | `POST /api/v1/render/jobs` |
| List | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | `GET /api/v1/render/jobs` |
| Get | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` | `GET /api/v1/render/jobs/{jobId}` |
| Start | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start` | — |
| Cancel | — | `POST /api/v1/render/jobs/{jobId}/cancel` |
| Retry | — | `POST /api/v1/render/jobs/{jobId}/retry` |

## Removed Routes

| Route | Reason | Callers Updated |
|-------|--------|----------------|
| `POST .../execute-local` | Stale, misleading semantics, no approved consumer | Smoke script, test |

## Why Compatibility Was Not Fully Preserved

The 7 legacy RenderJob routes were NOT removed because the frontend (`frontend/src/api/`) is an active consumer. Route string corrections in the frontend are allowed but require understanding the tenant/project context availability. These routes should be migrated when frontend development resumes.

`execute-local` was removed because its only callers were a smoke script and a test — no approved production consumer.

## Real HTTP Evidence

| Route | Default | Preview | Dev Profile |
|-------|--------|---------|------------|
| `/dev/**` (4 gated Controllers) | 404 | 404 | Registered |
| `/api/v1/dev/auth/token` | 404 (property off) | 404 | 404 (property off) |
| `execute-local` | 404 | 404 | 404 |
| Canonical RenderJob | Available | Available | Available |

## Route Inventory After Correction

| Category | Before | After |
|----------|------:|------:|
| Dev mappings (test/preview) | 11 | 2 |
| execute-local | 1 | 0 |
| Legacy RenderJob routes | 7 | 7 (retained — frontend consumer) |

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
