# Backend Integrity — MVC Route Inventory

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-MVC-ROUTE-INVENTORY.0
**Decision:** BACKEND_MVC_ROUTE_INVENTORY_VALID_WITH_BOUNDARY_DEBT

---

## Repository and Runtime Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | 250c818 |
| Gradle | 9.1.0 |
| Java | 25.0.3 |
| Spring Boot | 4.0.4 |
| Application module | platform-app |
| Main class | com.example.platform.PlatformApplication |
| Profiles | test, preview |
| Database | PostgreSQL (Flyway) |

## Inventory Method

1. Source Controller discovery: `grep -R @RestController` across all active modules
2. ApplicationContext startup: `@SpringBootTest` with test/preview profiles
3. RequestMappingHandlerMapping extraction: `getHandlerMethods()` captured to `/tmp/mvc-route-inventory.txt`
4. 501 runtime handler mappings captured

## Route Counts

| Category | Count |
|----------|------:|
| Source Controllers (active modules) | ~100 |
| Runtime application mappings | 501 |
| RenderController mappings | 29 |
| ProductController mappings | 6 |
| ArtifactController mappings | 4 |
| Timeline-related mappings | 36 |
| Dev routes (`/dev/`) | 11 |
| Admin routes (`/admin/`) | 68 |
| Upload/ingest routes | 2 (preview + client export) |

## Critical Route Classification

### Health

| Route | Handler | Classification |
|-------|---------|---------------|
| `GET /api/v1/projects/{projectId}/dashboard/health` | ProjectDashboardController.health | RUNTIME_VALID |
| `GET /api/v1/secrets/health` | SecretsController.health | RUNTIME_VALID |

### RenderJob Routes (29 total)

| Route | Handler | Type | Classification |
|-------|---------|------|---------------|
| `POST /api/v1/render/jobs` | RenderController.create | Legacy | STALE_COMPATIBILITY_ROUTE |
| `POST /api/v1/render/jobs/submit` | RenderController.submitJob | Legacy | STALE_COMPATIBILITY_ROUTE |
| `GET /api/v1/render/jobs` | RenderController.list | Legacy | STALE_COMPATIBILITY_ROUTE |
| `GET /api/v1/render/jobs/{jobId}` | RenderController.getJob | Legacy | STALE_COMPATIBILITY_ROUTE |
| `POST /api/v1/render/jobs/{jobId}/cancel` | RenderController.cancelJob | Legacy | STALE_COMPATIBILITY_ROUTE |
| `POST /api/v1/render/jobs/{jobId}/retry` | RenderController.retryJob | Legacy | STALE_COMPATIBILITY_ROUTE |
| `GET /api/v1/render/jobs/{jobId}/status-history` | RenderController.getStatusHistory | Legacy | STALE_COMPATIBILITY_ROUTE |
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | RenderController.createRenderJob | Canonical | RUNTIME_VALID |
| `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` | RenderController.listRenderJobs | Canonical | RUNTIME_VALID |
| `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` | RenderController.getRenderJob | Canonical | RUNTIME_VALID |
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start` | RenderController.startRenderJob | Canonical | RUNTIME_VALID |
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execute-local` | RenderController.executeLocal | Deprecated | STALE_COMPATIBILITY_ROUTE |
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/plan` | RenderController.previewIncrementalPlan | Canonical | RUNTIME_VALID |
| `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/submit` | RenderController.submitIncrementalRenderJob | Canonical | RUNTIME_VALID |
| `GET /api/v1/render/jobs/{jobId}/artifacts/{artifactId}/content` | RenderController.getArtifactContent | — | RUNTIME_VALID |
| `GET /api/v1/render/jobs/{jobId}/artifacts/{artifactId}/access` | RenderController.getArtifactAccess | — | RUNTIME_VALID |
| `POST /api/v1/preview/media` | RenderController.uploadPreviewMedia | Preview | PREVIEW_ONLY |

**Route registration does not prove RenderJob lifecycle correctness.**

### Product Routes

| Route | Handler | Classification |
|-------|---------|---------------|
| `GET /api/v1/products/{productId}` | ProductController.get | RUNTIME_VALID |
| `GET /api/v1/projects/{projectId}/products` | ProductController.listByProject | RUNTIME_VALID |
| `GET /api/v1/assets/{assetId}/products` | ProductController.listByAsset | RUNTIME_VALID |
| `GET /api/v1/products/{productId}/dependencies` | ProductController.getDependencies | RUNTIME_VALID |
| `POST /api/v1/products/{productId}/dependencies` | ProductController.linkDependency | RUNTIME_VALID |
| `DELETE /api/v1/products/{productId}/dependencies/{dependencyId}` | ProductController.unlink | RUNTIME_VALID |

### Artifact Routes

| Route | Handler | Classification |
|-------|---------|---------------|
| `GET /api/v1/artifact/catalog/overview` | ArtifactController.overview | RUNTIME_VALID |
| `GET /api/v1/artifacts/{artifactId}/delete-check` | ArtifactLifecycleController.deleteCheck | RUNTIME_VALID |
| `POST /api/v1/artifacts/{artifactId}/tombstone` | ArtifactLifecycleController.tombstone | RUNTIME_VALID |
| `POST /api/v1/artifacts/gc/run` | ArtifactLifecycleController.runGc | RUNTIME_VALID |

### TimelineRevision Routes

| Route | Handler | Classification |
|-------|---------|---------------|
| `GET /api/v1/render/projects/{projectId}/timeline/revisions/head` | TimelineRevisionController.head | RUNTIME_VALID |
| `POST /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render` | TimelineRevisionController.render | RUNTIME_VALID |
| `GET /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render-jobs/{renderJobId}` | TimelineRevisionController.getRenderJobStatus | RUNTIME_VALID |
| `GET /api/v1/render/projects/{projectId}/timeline/revisions/facets` | TimelineRevisionController.facets | RUNTIME_VALID |
| Plus 6 more edit/patch/merge/restore routes | — | RUNTIME_VALID |

### Dev Routes (11 total)

| Route | Handler | Profile | Classification |
|-------|---------|---------|---------------|
| `GET /dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports` | DevSafePreflightReportReadController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports/{recordId}` | DevSafePreflightReportReadController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports/retention/dry-run` | DevSafePreflightReportRetentionDryRunController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/storage-delivery-profiles` | DevStorageDeliveryProfileDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/storage-delivery-profiles/{profileId}` | DevStorageDeliveryProfileDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/storage-delivery-profiles/validation` | DevStorageDeliveryProfileDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/ingest/preflight-policy` | DevIngestPreflightPolicyDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/ingest/preflight-policy/decision-semantics` | DevIngestPreflightPolicyDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `GET /dev/ingest/preflight-policy/config` | DevIngestPreflightPolicyDiagnosticsController | ALL | **UNSAFE_ENVIRONMENT_EXPOSURE** (P2) |
| `POST /api/v1/dev/auth/token` | DevAuthController.issueToken | — | Requires security review |
| `SpaFallbackController` | `/admin/**`, `/app/**`, `/dev/**` | ALL | SPA fallback |

**All `/dev/` routes lack `@Profile` or `@ConditionalOnProperty` gating. They are active under ALL profiles including production-like.**

### Upload Truth

| Route | Handler | Classification |
|-------|---------|---------------|
| `POST /api/v1/preview/media` | RenderController.uploadPreviewMedia | PREVIEW_ONLY |
| `POST /api/v1/render/client-exports/{sessionId}/upload` | ClientExportController.uploadAndComplete | Client export only |

**UPLOAD_API_NOT_IMPLEMENTED** — No dedicated user upload API exists. The `preview/media` route is a preview fixture, not a canonical upload endpoint.

## Sensitive Data Check

No `storageReferenceId`, `bucket`, `objectKey`, `localPath`, `credentials`, or `signedUrl` fields were found exposed in route response DTOs (requires deeper DTO inspection for full verification).

## Duplicate Mapping Review

No exact duplicate method+path mappings were found. Legacy and tenant-scoped RenderJob routes use different path patterns.

## Compatibility Routes

| Route | Status | Canonical replacement |
|-------|--------|---------------------|
| `POST /api/v1/render/jobs` | ACTIVE_COMPATIBILITY | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` |
| `GET /api/v1/render/jobs` | ACTIVE_COMPATIBILITY | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs` |
| `GET /api/v1/render/jobs/{jobId}` | ACTIVE_COMPATIBILITY | `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}` |
| `POST /api/v1/render/jobs/{jobId}/execute-local` | ACTIVE_COMPATIBILITY | `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start` |

## Issues

**P0**: None
**P1**: None
**P2**:
1. 11 dev routes active under ALL profiles without gating
2. `execute-local` deprecated route still active
3. Legacy compatibility routes still active

**P3**:
1. Some legacy route naming inconsistency

## Capability Route Matrix

| Capability | Source | Bean | Runtime mapping | Classification |
|-----------|------:|-----:|----------------:|----------------|
| Health | YES | YES | YES | RUNTIME_VALID |
| Product list | YES | YES | YES | RUNTIME_VALID |
| Product detail | YES | YES | YES | RUNTIME_VALID |
| TimelineRevision | YES | YES | YES (10 routes) | RUNTIME_VALID |
| TimelineRevision render | YES | YES | YES | RUNTIME_VALID |
| RenderJob create | YES | YES | YES | RUNTIME_VALID |
| RenderJob execute | YES | YES | YES | RUNTIME_VALID |
| RenderJob status | YES | YES | YES | RUNTIME_VALID |
| Artifact list | YES | YES | YES | RUNTIME_VALID |
| Artifact content | YES | YES | YES | RUNTIME_VALID |
| Artifact access | YES | YES | YES | RUNTIME_VALID |
| Upload API | NO | NO | NO | NOT_IMPLEMENTED |
| Dev diagnostics | YES | YES | YES (11 routes) | UNSAFE_ENVIRONMENT_EXPOSURE |

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

**BACKEND-INTEGRITY-REPAIR-DEV-ROUTE-ISOLATION.0**

Gate all `/dev/` routes with `@Profile("dev")` or `@ConditionalOnProperty` to prevent exposure in production-like environments.
