# Backend Integrity — Admin Boundary Remainder

## Status

```text
BACKEND-INTEGRITY-ADMIN-BOUNDARY-REMAINDER.0:
COMPLETE
```

## Decision

```text
ADMIN_BOUNDARY_REMAINDER_CLOSED
```

## Baseline

```text
base commit: 7074f84
new commit: (this commit)
application module: platform-app
main class: com.example.platform.PlatformApplication
security-enabled runtime: YES, Testcontainers PostgreSQL
previous Admin mutation 500: REPRODUCED AND CORRECTED
identity/admin route: CLASSIFIED AND VERIFIED
tenants-ai group: CLASSIFIED
```

## Admin Mutation 500 Reproduction and Correction

### Root Cause

```text
route: POST /api/v1/admin/feature-flags
request: {} (empty JSON body)
exception: NullPointerException
source: FeatureFlagJdbcStore.save() line 68 — definition.flagType().name() when flagType is null
classification: METHOD_ARGUMENT_VALIDATION_NOT_MAPPED
```

The `CreateFlagRequest` record had no Bean Validation annotations, and the controller lacked `@Valid` on `@RequestBody`. When `flagType` was null, the JDBC store threw NPE, which fell through to the generic `Exception` handler → 500.

### Correction

1. Added `@NotBlank` on `CreateFlagRequest.flagKey`
2. Added `@NotNull` on `CreateFlagRequest.flagType`
3. Added `@Valid` on `@RequestBody CreateFlagRequest` in both `createFlag()` and `updateFlag()` methods

The existing `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` now catches validation failures and returns 400.

### Additional Exception Mappings

Added handlers for:
- `SecurityException` → 403 (used by `TenantProjectController.listAllTenants()`)
- `AccessDeniedException` → 403 (used by `TenantAiAdminController`)

Both previously fell through to the generic 500 handler.

## Exception Mapping Matrix

| Exception | HTTP Status | Handler |
| --------- | ----------: | ------- |
| NoHandlerFoundException | 404 | handleNoHandler |
| PlatformException | varies | handlePlatform |
| MethodArgumentNotValidException | 400 | handleValidation |
| BindException | 400 | handleValidation |
| IllegalArgumentException | 400 | handleIllegalArgument |
| IllegalStateException | 409 | handleIllegalState |
| SecurityException | 403 | handleSecurity (NEW) |
| AccessDeniedException | 403 | handleAccessDenied (NEW) |
| Exception (generic) | 500 | handleUnknown |

## Identity Admin Route Inventory

| Property | Value |
| -------- | ----- |
| Module | identity-access-module |
| Controller | TenantProjectController |
| Handler | listAllTenants() |
| Method/path | GET /api/v1/identity/admin/tenants |
| Current consumers | Frontend admin UI |
| Security coverage | `/api/v1/**` → authenticated() + programmatic `isAdmin()` check |
| Resource ownership | Identity-domain tenant administration |

## Identity Admin Route Decision

```text
CANONICAL_IDENTITY_ADMIN_RESOURCE
```

The route is about identity administration (listing all tenants), which is correctly owned by the identity module. The path `/api/v1/identity/admin/tenants` is consistent with the identity module's namespace. It does not duplicate `/api/v1/admin/**` functionality — it provides tenant listing from the identity domain.

The route has explicit Admin protection via:
1. `/api/v1/**` → `authenticated()` (URL matcher)
2. `isAdmin()` programmatic check (throws `SecurityException` → 403)

## Identity Admin Security Matrix

| Identity | HTTP Status | Classification |
| -------- | ----------: | -------------- |
| Anonymous | 401 | AUTHENTICATION_REJECTED |
| Non-admin | 403 | AUTHORIZATION_REJECTED |
| Admin | 200 | HANDLER_SUCCEEDED |

## `tenants-ai` Authority Model

```text
GLOBAL_PLATFORM_ADMIN
```

Evidence:
- `TenantAiAdminController` is under `/api/v1/admin/tenants/{tenantId}/ai`
- Protected by `hasAuthority("ROLE_ADMIN")` URL matcher
- The `tenantId` path variable specifies WHICH tenant's AI key to manage
- `ROLE_ADMIN` can manage ANY tenant's AI key — this is global platform administration
- No tenant-scoped admin role exists in the current security model

## Tenant Scope Evidence

Since the authority model is `GLOBAL_PLATFORM_ADMIN`:
- Same-tenant testing: NOT_APPLICABLE (global admin can access any tenant)
- Cross-tenant testing: VERIFIED — global admin intentionally has cross-tenant access
- Non-admin users cannot access any tenant's AI keys (403)
- Anonymous users cannot access any tenant's AI keys (401)

```text
UNAPPROVED_CROSS_TENANT_EXPOSURE: NO
```

## Query and Service Scope

Tenant enforcement for `tenants-ai`:
- URL layer: `hasAuthority("ROLE_ADMIN")` on `/api/v1/admin/**`
- Method layer: `requireAdminRole()` programmatic check
- Service layer: `TenantLitellmKeyService` uses `tenantId` parameter directly
- The `tenantId` is caller-provided via URL path, but authorization is global-admin-only

## Admin Regression Matrix

| Group | Anonymous | Non-admin | Admin |
| ----- | --------: | --------: | ----: |
| feature-flags read | 401 | 403 | 200 |
| feature-flags mutation (invalid) | 401 | 403 | 400 |
| billing/plans read | 401 | 403 | — |
| delivery/destinations read | 401 | 403 | — |
| identity/admin/tenants | 401 | 403 | 200 |
| notifications mutation | 401 | 403 | — |

## HTTP Boundary Regression

| Route | Status |
| ----- | ------: |
| execute-local | 404 |
| retry | 404 |
| old create alias | 404 |
| old submit alias | 404 |
| old detail alias | 404 |
| old list alias | 404 |
| unknown /api | 404 |
| unknown /dev | 404 |
| unknown /admin | 404 |

## Error Response Safety

| Check | Result |
| ----- | ------ |
| Stack trace exposed | NO |
| Exception class exposed | NO |
| Internal path exposed | NO |
| Credentials exposed | NO |
| Storage internals exposed | NO |

## Issues

```text
P0: NONE
P1: NONE
P2: NONE
P3: NONE
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
OIDC PostgreSQL mismatch
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Admin route and error-contract corrections were integrity work, not capability expansion.
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
