# VS.1-PRODUCT — Product/Artifact Response Flow

**TASK_ID:** VS.1-PRODUCT
**Status:** COMPLETED
**Date:** 2026-07-01

---

## Summary

Implemented Product/Artifact response flow for preview render results. The implementation provides a safe, read-only query layer that integrates ProductRuntime and StorageRuntime to produce API-safe response DTOs.

## Deliverables

### 1. PreviewArtifactResponse DTO

**File:** `render-module/src/main/java/com/example/platform/render/app/product/PreviewArtifactResponse.java`

Response record carrying safe Product metadata and storage size/checksum info. Does NOT expose:
- Internal filesystem paths (rootPath, relativePath, absolutePath)
- Storage provider internals (bucket, key, signed URLs)
- Provider/backend/environment selection details

### 2. PreviewArtifactQueryService

**File:** `render-module/src/main/java/com/example/platform/render/app/product/PreviewArtifactQueryService.java`

Read-only service that integrates:
- **ProductRuntime** (Product lifecycle) via `ProductRuntimeService`
- **StorageRuntime** (StorageReference metadata) via `StorageRuntimeService`

Query methods:
- `findByProductId(id)` — full response with dependency details
- `findByProductIdShallow(id)` — response with counts but no dependency IDs
- `findLatestPreviewByAsset(assetId)` — latest PREVIEW product for an asset
- `findLatestByAssetAndType(assetId, type)` — latest product of specific type
- `findByProject(projectId, limit)` — products for a project
- `findByAsset(assetId)` — products for an asset
- `findByTimelineRevision(revisionId)` — products by timeline revision

### 3. Comprehensive Tests

**File:** `render-module/src/test/java/com/example/platform/render/app/product/PreviewArtifactQueryServiceTest.java`

20 tests covering:
- Product lookup by ID with storage metadata
- Shallow vs deep dependency resolution
- Product lookup by project, asset, timeline revision
- Latest preview/asset product lookup
- Missing storage reference graceful handling
- Architecture boundary enforcement (no paths, no signed URLs, no provider internals)

## Architecture Boundaries Enforced

| Boundary | Enforcement |
|---|---|
| No local paths in response | Response record has no path fields; test verifies toString() |
| No signed URLs | Test verifies no signedUrl/presigned in response |
| No provider internals | Response has no providerType/bucket/key fields |
| ProductRuntime owns lifecycle | Service uses ProductRuntimeService exclusively |
| StorageRuntime owns materialization | Service uses StorageRuntimeService for storage metadata |
| Read-only | Service has no write/modify methods |

## Test Results

```
PreviewArtifactQueryServiceTest: 20/20 PASSED
RenderOutputRegistrationServiceTest: all PASSED (existing, unaffected)
Build: SUCCESSFUL
```

## Files Created/Modified

| File | Action |
|---|---|
| `render-module/src/main/java/com/example/platform/render/app/product/PreviewArtifactResponse.java` | CREATED |
| `render-module/src/main/java/com/example/platform/render/app/product/PreviewArtifactQueryService.java` | CREATED |
| `render-module/src/test/java/com/example/platform/render/app/product/PreviewArtifactQueryServiceTest.java` | CREATED |

## Stop Condition Verification

| Condition | Status |
|---|---|
| Local paths in API response | ✅ NOT PRESENT |
| Provider/storage internals exposed | ✅ NOT PRESENT |
| ProductRuntime/StorageRuntime bypassed | ✅ NOT BYPASSED |
