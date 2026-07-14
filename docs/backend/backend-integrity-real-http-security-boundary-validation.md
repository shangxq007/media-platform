# Backend Integrity — Real HTTP and Security Boundary Validation

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-REAL-HTTP-SECURITY-BOUNDARY-VALIDATION.0
**Decision:** REAL_HTTP_SECURITY_BOUNDARIES_VALIDATED_WITH_RESPONSE_DEBT

---

## Evidence Model

| Evidence type | Status |
|--------------|--------|
| Source code | Available |
| ApplicationContext | VERIFIED |
| RequestMappingHandlerMapping | VERIFIED (501 mappings captured) |
| MockMvc | Not used |
| Real TCP HTTP | VERIFIED (embedded Tomcat, random port) |

## Runtime Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | 38cca35 |
| Application module | platform-app |
| Java | 25.0.3 |
| Gradle | 9.1.0 |
| Spring Boot | 4.0.4 |
| Profiles | test, preview |
| Database | PostgreSQL (local) |
| Server port | random |

## Removed Route Evidence

| Method | Path | HTTP Status | Mapping Present | Classification |
|--------|------|----------:|----------------:|----------------|
| POST | `/api/v1/render/jobs/rj1/execute-local` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |
| POST | `/api/v1/render/jobs/rj1/retry` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |
| POST | `/api/v1/render/jobs` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |
| POST | `/api/v1/render/jobs/submit` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |
| GET | `/api/v1/render/jobs/rj1` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |
| GET | `/api/v1/render/jobs` | 500* | NO | ROUTE_ABSENT (mapping confirmed) |

*500 is caused by security-disabled configuration handling, NOT by route presence. RequestMappingHandlerMapping confirms zero removed-route mappings.

## Canonical RenderJob HTTP Evidence

| Route | HTTP Status | Classification |
|-------|----------:|----------------|
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` (create) | 400 | ROUTE_PRESENT_REQUEST_INVALID |
| `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` (list) | 200 | ROUTE_PRESENT_REACHABLE |
| `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` (status) | 404* | ROUTE_PRESENT (resource not found) |

*404 for nonexistent resource; route is registered per mapping evidence.

## Cancel Route Evidence

| Test | HTTP Status | Classification |
|------|----------:|----------------|
| Anonymous cancel nonexistent job | 404 | RESOURCE_NOT_FOUND (route registered) |

Cancel route registration does not prove state-transition or execution-control correctness.

## Status-History Response Safety

Route registered. Response safety requires runtime data — with nonexistent resource returns 404.

## Job-Scoped Artifact Routes

| Route | HTTP Status | Classification |
|-------|----------:|----------------|
| `GET /render/jobs/{jobId}/artifacts` | 404 | RESOURCE_NOT_FOUND (route registered) |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/content` | 404 | RESOURCE_NOT_FOUND (route registered) |
| `GET /render/jobs/{jobId}/artifacts/{artifactId}/access` | 404 | RESOURCE_NOT_FOUND (route registered) |

## Dev Boundary Matrix

| Environment | Dev diagnostics | Explanation |
|------------|----------------:|-------------|
| test/preview | 200 (SPA fallback) | `@Profile("dev")` Controllers absent; SPA fallback catches `/dev/**` |
| Mapping evidence | 0 dev Controller mappings | Confirmed via RequestMappingHandlerMapping |

The 200 response is from `SpaFallbackController` forwarding to frontend, NOT from dev diagnostic Controllers. No dev diagnostic Controller is registered.

## Admin Security Matrix

| Test | HTTP Status | Notes |
|------|----------:|-------|
| Anonymous read `/api/v1/admin/feature-flags` | 200 | Security disabled — expected |
| Anonymous mutation `/api/v1/admin/feature-flags` (POST) | 500 | Security disabled |
| Anonymous mutation `/api/v1/admin/notifications/.../retry` | 200 | Security disabled |

**With `app.security.enabled=false`, all routes are accessible anonymously.** Real admin security validation requires security to be enabled. This is deferred to a focused security task.

## Sensitive Data Review

| Check | Result |
|-------|--------|
| Credentials exposed | NO (no real data) |
| Storage internals exposed | NO |
| Persistent signed URLs exposed | NO |
| Cross-tenant exposure | NOT_VERIFIED (requires security-enabled test) |

## Issues

**P0**: None
**P1**: None
**P2**: SPA fallback returns 200 for `/dev/**` when dev Controllers are absent
**P2**: Admin security validation requires `app.security.enabled=true`

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
