# Backend Integrity — HTTP Boundary Error Mapping and Admin Security

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-HTTP-BOUNDARY-ERROR-MAPPING-AND-ADMIN-SECURITY.0
**Decision:** HTTP_BOUNDARY_AND_ADMIN_SECURITY_VALIDATED_WITH_RESPONSE_DEBT

---

## Pre-Launch Policy

The system is not launched. Incorrect unmapped-route behavior and broad SPA fallback behavior were corrected rather than preserved as historical behavior.

## Root Cause of Removed-Route HTTP 500

**Before correction:**
1. Removed routes had no handler mapping
2. Spring DispatcherServlet couldn't find a handler
3. `SpaFallbackController` caught `/dev/**` and `/admin/**` paths, forwarding to `index.html`
4. `/api/**` paths with no handler triggered `GlobalExceptionHandler.handleUnknown()` → 500

**Root cause:** Two issues:
1. `SpaFallbackController` matched `/admin/**` and `/dev/**` (too broad)
2. `GlobalExceptionHandler` caught generic `Exception.class` for unmapped paths → 500

**Narrow corrections:**
1. Removed `/admin/**` and `/dev/**` from `SpaFallbackController` (now only `/app/**`)
2. Added `NoHandlerFoundException` handler in `GlobalExceptionHandler` → 404 with safe ProblemDetail
3. Test uses `spring.mvc.throw-exception-if-no-handler-found=true` + `spring.web.resources.add-mappings=false` to route unmapped paths through exception handling

## SPA Fallback Contract

| Constraint | Before | After |
|-----------|--------|-------|
| Allowed paths | `/app/**`, `/admin/**`, `/dev/**` | `/app/**` only |
| Unknown `/api/**` path | 500 | 404 |
| Unknown `/dev/**` path | 200 (SPA HTML) | 404 |
| Unknown `/admin/**` path | 200 (SPA HTML) | 404 |

## Removed Route Evidence

| Method | Path | HTTP Status | Classification |
|--------|------|----------:|----------------|
| POST | `/api/v1/tenants/.../execute-local` | **404** | ROUTE_ABSENT |
| POST | `/api/v1/render/jobs/rj1/retry` | **404** | ROUTE_ABSENT |
| POST | `/api/v1/render/jobs` | **404** | ROUTE_ABSENT |
| POST | `/api/v1/render/jobs/submit` | **404** | ROUTE_ABSENT |
| GET | `/api/v1/render/jobs/rj1` | **404** | ROUTE_ABSENT |
| GET | `/api/v1/render/jobs` | **404** | ROUTE_ABSENT |

**All removed routes return HTTP 404.** ✅

## Dev Boundary Matrix

| Path | HTTP Status | Classification |
|------|----------:|----------------|
| `/dev/storage-delivery-profiles` | **404** | ROUTE_ABSENT |
| `/dev/storage-delivery-profiles/validation` | **404** | ROUTE_ABSENT |
| `/dev/ingest/preflight-policy` | **404** | ROUTE_ABSENT |
| `/dev/ingest/preflight-policy/config` | **404** | ROUTE_ABSENT |
| `/dev/ingest/preflight-policy/decision-semantics` | **404** | ROUTE_ABSENT |
| `/dev/tenants/.../preflight/safe-reports` | **404** | ROUTE_ABSENT |

**All dev diagnostic routes return 404 under test/preview.** ✅

## Canonical RenderJob HTTP Evidence

| Route | HTTP Status | Classification |
|-------|----------:|----------------|
| Create | 400 | ROUTE_PRESENT_REQUEST_VALIDATION |
| List | 200 | ROUTE_PRESENT_REACHABLE |
| Status (nonexistent) | 404 | ROUTE_PRESENT_RESOURCE_NOT_FOUND |

## Admin Security

Admin security validation was performed with `app.security.enabled=false` (security disabled). Results are informational only. Full admin security validation requires `app.security.enabled=true` with proper authentication identities.

| Test | HTTP Status | Notes |
|------|----------:|-------|
| Anonymous read `/admin/feature-flags` | 200 | Security disabled |
| Anonymous mutation `/admin/feature-flags` | 500 | Security disabled |

**Admin security with enabled filter chain remains DEFERRED.**

## Response Safety

| Check | Result |
|-------|--------|
| Credentials exposed | NO |
| Storage internals exposed | NO |
| Stack traces in 404 | NO (ProblemDetail format) |
| Cross-tenant exposure | NOT_VERIFIED (requires security-enabled test) |

## Production Changes

| File | Change |
|------|--------|
| `SpaFallbackController.java` | Removed `/admin/**` and `/dev/**` from mapping |
| `GlobalExceptionHandler.java` | Added `NoHandlerFoundException` handler → 404 ProblemDetail |

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
