# Backend Integrity — Enabled Admin Security Validation

## Status

```text
BACKEND-INTEGRITY-ENABLED-ADMIN-SECURITY-VALIDATION.0:
COMPLETE
```

## Decision

```text
ENABLED_ADMIN_SECURITY_VALIDATED
```

## Baseline

```text
base commit: 7b6ee5e
new commit: (this commit)
application module: platform-app
main class: com.example.platform.PlatformApplication
security previously disabled: YES (app.security.enabled=false)
known Admin mapping count: ~68 (under /api/v1/admin/**)
known HTTP boundary fixes: SPA fallback narrowed, NoHandlerFoundException handler added
```

## Security Enablement Contract

```text
property: app.security.enabled=true
additional: app.security.oauth2.enabled=true
profiles: test, preview
required Beans: JwtDecoder (mocked via @MockitoBean → LegacyHmacJwtDecoder)
```

The `OAuth2ResourceServerSecurityConfiguration` requires both `app.security.enabled=true` and `app.security.oauth2.enabled=true`. For test purposes, `@MockitoBean` replaces the OIDC `JwtDecoder` with a `LegacyHmacJwtDecoder` that uses the test HMAC secret key.

## Effective SecurityFilterChains

| Chain | Order | Matcher | Admin coverage | CSRF | Authentication |
| ----- | ----: | ------- | -------------: | ---- | -------------- |
| oauth2SecurityFilterChain | 2 | `/**` | `/api/v1/admin/**` → `hasAuthority("ROLE_ADMIN")` | Disabled (stateless JWT) | OAuth2 JWT Bearer |

## SecurityHttpRules Authorization

```text
OPTIONS /**                  → permitAll
/swagger-ui/**, /v3/api-docs/** → permitAll
/actuator/**                 → permitAll
/api/v1/webhooks/**          → permitAll
/api/v1/mcp/**               → authenticated
/api/v1/dev/auth/**          → permitAll
/api/v1/admin/**             → hasAuthority("ROLE_ADMIN")  ← NEW
/api/v1/**                   → authenticated
anyRequest                   → permitAll
```

The `hasAuthority("ROLE_ADMIN")` rule was added to enforce role-based authorization for admin API endpoints.

## Admin Route Groups

| Group | Base path | Required authority |
| ----- | --------- | ------------------ |
| Admin feature flags | /api/v1/admin/feature-flags | ROLE_ADMIN |
| Admin billing | /api/v1/admin/billing/** | ROLE_ADMIN |
| Admin delivery | /api/v1/admin/delivery/** | ROLE_ADMIN |
| Admin notifications | /api/v1/admin/notifications/** | ROLE_ADMIN |
| Admin platform | /api/v1/admin/platform/** | ROLE_ADMIN |
| Admin tenants AI | /api/v1/admin/tenants/{tenantId}/ai/** | ROLE_ADMIN |
| Admin shared resources | /api/v1/admin/shared-resources/** | ROLE_ADMIN |
| Admin navigation | /api/v1/admin/navigation/** | ROLE_ADMIN |

## Test Identities

| Identity | Label | Authorities | Tenant claim |
| -------- | ----- | ----------- | ------------ |
| Anonymous | none | none | none |
| Non-admin | test-user | ROLE_USER | tenant-1 |
| Admin | test-admin | ROLE_ADMIN, ROLE_USER | tenant-1 |

## Authentication Matrix

| Identity | Admin read | Admin mutation |
| -------- | ---------- | -------------- |
| Anonymous | 401 | 401 |
| Non-admin | 403 | 403 |
| Admin | 200 | 500 (handler boundary reached, invalid body) |

## CSRF Versus Authorization

CSRF is disabled globally (stateless JWT). Non-admin rejection is purely from authorization (`hasAuthority("ROLE_ADMIN")`), not CSRF.

## Anonymous Admin Evidence

| Route | Status |
| ----- | ------: |
| GET /api/v1/admin/feature-flags | 401 |
| GET /api/v1/admin/billing/plans | 401 |
| GET /api/v1/admin/delivery/destinations | 401 |
| GET /api/v1/identity/admin/tenants | 401 |
| POST /api/v1/admin/feature-flags | 401 |
| PUT /api/v1/admin/notifications/events/test | 401 |
| POST /api/v1/admin/notifications/deliveries/d1/retry | 401 |

**ANONYMOUS_ADMIN_READ_2XX_COUNT: 0** ✅
**ANONYMOUS_ADMIN_MUTATION_2XX_COUNT: 0** ✅

## Non-Admin Evidence

| Route | Status |
| ----- | ------: |
| GET /api/v1/admin/feature-flags | 403 |
| GET /api/v1/admin/billing/plans | 403 |
| GET /api/v1/admin/platform/readiness | 403 |
| POST /api/v1/admin/feature-flags | 403 |
| PUT /api/v1/admin/notifications/events/test | 403 |

**NON_ADMIN_READ_2XX_COUNT: 0** ✅
**NON_ADMIN_MUTATION_2XX_COUNT: 0** ✅
**NON_ADMIN_REJECTION_LAYER: AUTHORIZATION** ✅

## Authorized Admin Evidence

| Route | Status | Notes |
| ----- | ------: | ----- |
| GET /api/v1/admin/feature-flags | 200 | Read boundary reached ✅ |
| POST /api/v1/admin/feature-flags | 500 | Mutation boundary reached (invalid body → handler exception) ✅ |

**AUTHORIZED_ADMIN_READ_BOUNDARY: REACHED** ✅
**AUTHORIZED_ADMIN_MUTATION_BOUNDARY: REACHED_SAFELY** ✅

## Removed Route Evidence (Security-Enabled)

| Route | Method | Status | Classification |
| ----- | ------ | ------: | -------------- |
| /api/v1/render/jobs/rj1 | GET | 404 | ROUTE_ABSENT |
| /api/v1/render/jobs | GET | 404 | ROUTE_ABSENT |
| /api/v1/render/jobs | POST | 404 | ROUTE_ABSENT |
| /api/v1/render/jobs/submit | POST | 404 | ROUTE_ABSENT |
| /api/v1/render/jobs/rj1/retry | POST | 404 | ROUTE_ABSENT |
| /api/v1/tenants/t1/projects/p1/render-jobs/rj1/execute-local | POST | 404 | ROUTE_ABSENT |

**ALL_REMOVED_AUTHORIZED_RESULTS: 404** ✅

## SPA Fallback Regression (Security-Enabled)

| Path | Status | Classification |
| ---- | ------: | -------------- |
| /api/v1/does-not-exist | 404 | Not SPA HTML ✅ |
| /dev/does-not-exist | 404 | Not SPA HTML ✅ |
| /admin/does-not-exist | 404 | Not SPA HTML ✅ |

**BACKEND_PATH_RETURNED_SPA_HTML: NO** ✅

## Dev Boundary (Security-Enabled)

| Path | Status |
| ---- | ------: |
| /dev/storage-delivery-profiles | 404 |
| /dev/ingest/preflight-policy | 404 |

**DEV_ROUTES_ABSENT: YES** ✅

## Error Response Safety

Anonymous admin rejection returns empty body with 401 status. No stack traces, credentials, or internal paths exposed.

## Issues

```text
P0: NONE
P1: NONE
P2: NONE
P3: /api/v1/identity/admin/tenants is an admin-like endpoint outside /api/v1/admin/** — should be consolidated
```

## Remaining Unverified Areas

```text
RenderJob lifecycle transitions
cancel execution-control correctness
Provider registration
Provider selection
successful FFmpeg execution
Artifact delivery end-to-end
upload API
constructor-injection inventory
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.
```

## Recommended Next Task

```text
BACKEND-INTEGRITY-PROVIDER-REGISTRATION-VALIDATION.0
```
