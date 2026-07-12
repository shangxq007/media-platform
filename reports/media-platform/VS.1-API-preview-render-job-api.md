# VS.1-API — Preview Render Job API Contract and Application Boundary

**Task ID:** VS.1-API
**Status:** COMPLETED
**Date:** 2026-07-01

---

## Objective

Implement Preview Render Job API contract and application layer: create job, query status, return product/artifact metadata.

## Deliverables

### Domain Layer (`render-module/src/main/java/com/example/platform/render/domain/previewjob/`)

| File | Description |
|------|-------------|
| `PreviewRenderJobId.java` | Strongly-typed value object for job identifiers. Rejects null/blank. |
| `PreviewRenderJobStatus.java` | Lifecycle enum: QUEUED → EXECUTING → COMPLETED/FAILED/CANCELLED. Terminal state tracking. |
| `PreviewRenderJob.java` | Domain aggregate root with deterministic state transitions. Factory method `create()`, transition methods `startExecuting()`, `complete()`, `fail()`, `cancel()`. All transitions are fail-closed. |
| `PreviewRenderJobRepository.java` | Domain port interface for persistence. Methods: `save`, `findById`, `findByIdAndTenantAndProject`, `listByTenantAndProject`, `updateStatus`. |

### Application Layer (`render-module/src/main/java/com/example/platform/render/app/preview/`)

| File | Description |
|------|-------------|
| `CreatePreviewRenderJobRequest.java` | Request DTO. Fields: `tenantId`, `projectId`, `snapshotId`, `profile` (defaults to "default_1080p"). Jakarta Validation annotations. |
| `PreviewRenderJobResponse.java` | Response DTO. Safe fields only: jobId, tenantId, projectId, snapshotId, profile, status, outputProductId, errorMessage, createdAt, completedAt. Factory method `fromDomain()`. |
| `PreviewRenderJobArtifactResponse.java` | Artifact metadata DTO. Safe fields: renderJobId, projectId, outputProductId, productStatus, mimeType, outputFormat, width, height, fps, durationSeconds, hasSubtitles, inputProductIds, inputDependencyCount, createdAt, completedAt, message. |
| `PreviewRenderJobService.java` | Application service. Methods: `create()`, `getStatus()`, `list()`, `getArtifacts()`. Coordinates domain operations, ProductRuntime, and ProductDependencyRepository. |
| `InMemoryPreviewRenderJobRepository.java` | Thread-safe in-memory repository for testing. |
| `package-info.java` | Spring Modulith `@NamedInterface("preview")` annotation. |

### Tests (`render-module/src/test/java/com/example/platform/render/app/preview/`)

| File | Tests |
|------|-------|
| `PreviewRenderJobServiceTest.java` | **28 tests** across 6 nested test classes |

**Test breakdown:**
- **CreateTests** (5): happy path, profile default, empty profile, custom profile, persistence
- **GetStatusTests** (4): found, not found, tenant mismatch, project mismatch
- **ListTests** (3): multiple jobs, empty list, tenant isolation
- **GetArtifactsTests** (5): completed with product, not completed, product not found, failed job, tenant mismatch
- **SafetyTests** (3): no storage reference IDs, no local paths, fail-closed transitions
- **DomainTests** (8): full lifecycle completed, full lifecycle failed, cancel from queued, fail from queued, cannot cancel executing, ID validation, null args, null product ID

## Architecture Boundaries Preserved

| Constraint | Status |
|------------|--------|
| No local paths in API response | ✅ No path fields on any DTO |
| No provider/storage internals in API response | ✅ No storageReferenceId, signedUrl, or provider fields |
| ProductRuntime/StorageRuntime boundaries preserved | ✅ Uses `ProductRuntimeService.find()` for product lookup |
| FFmpeg/libass only | ✅ No Remotion, no Artifact DAG, no Spring AI |
| No Flyway V1 changes | ✅ No DB migrations |
| No forbidden paths touched | ✅ Verified via `git diff` |

## Test Commands

```bash
./gradlew :render-module:compileJava
./gradlew :render-module:test --tests "com.example.platform.render.app.preview.*"
```

## Results

- **Compilation:** ✅ BUILD SUCCESSFUL
- **Tests:** ✅ 28/28 passed, 0 failures, 0 errors
- **Forbidden paths:** ✅ None touched

## Files Created/Modified

**Created (10 files):**
1. `render-module/src/main/java/com/example/platform/render/domain/previewjob/PreviewRenderJobId.java`
2. `render-module/src/main/java/com/example/platform/render/domain/previewjob/PreviewRenderJobStatus.java`
3. `render-module/src/main/java/com/example/platform/render/domain/previewjob/PreviewRenderJob.java`
4. `render-module/src/main/java/com/example/platform/render/domain/previewjob/PreviewRenderJobRepository.java`
5. `render-module/src/main/java/com/example/platform/render/app/preview/CreatePreviewRenderJobRequest.java`
6. `render-module/src/main/java/com/example/platform/render/app/preview/PreviewRenderJobResponse.java`
7. `render-module/src/main/java/com/example/platform/render/app/preview/PreviewRenderJobArtifactResponse.java`
8. `render-module/src/main/java/com/example/platform/render/app/preview/PreviewRenderJobService.java`
9. `render-module/src/main/java/com/example/platform/render/app/preview/InMemoryPreviewRenderJobRepository.java`
10. `render-module/src/main/java/com/example/platform/render/app/preview/package-info.java`
11. `render-module/src/test/java/com/example/platform/render/app/preview/PreviewRenderJobServiceTest.java`

**Modified:** None (zero existing files changed)
