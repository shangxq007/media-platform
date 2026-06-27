---
status: implementation-report
created: 2026-06-26
scope: render-module + platform-app + V1 baseline + shared-kernel
truth_level: current
owner: platform
---

# Foundation F3 — Storage Runtime Foundation

## Implemented

### Domain Models (3)
| Model | Purpose |
|-------|---------|
| `StorageReference` | Aggregate root: storageReferenceId, providerType, storageClass, rootPath, relativePath, checksum, contentHash, fileSize, mimeType, timestamps. `absolutePath()` concatenates root+relative. |
| `StorageProviderType` | LOCAL, S3, S3_COMPATIBLE, OBJECT_STORAGE |
| `StorageClass` | STANDARD, ARCHIVE, TEMPORARY, CACHE |

### Schema
`storage_reference` table — 11 columns, unique(providerType, rootPath, relativePath). 2 indexes (checksum, contentHash).

### Repository + Service
| Component | Key Methods |
|-----------|-------------|
| `StorageReferenceRepository` | save (ON CONFLICT), findById, findByContentHash, exists, delete |
| `StorageRuntimeService` | register, materialize (returns local path), verifyChecksum (SHA-256), find, exists, delete |

### Events + Controller
| Component | Purpose |
|-----------|---------|
| `StorageReferenceRegisteredEvent` | storageReferenceId, providerType, absolutePath, fileSize |
| `StorageController` | GET /api/v1/storage/{id} — read-only |

### Architecture
- V1: LOCAL provider only; S3-compatible via R10A
- materialize() returns local file path
- verifyChecksum() computes SHA-256 of file and compares with stored checksum
- No signed URLs, no credentials, no upload/download API
- Storage remains optional for Products (storageReferenceId nullable)

### Product + Execution Integration
- Products reference storage via `storageReferenceId` (nullable)
- ExecutionBackend receives StorageReferenceId → StorageRuntimeService.materialize() → localPath
- ExecutionBackend never resolves storage itself

## Tests
Compilation passes. Existing tests unaffected.

## Known Limitations
| Limitation | Status |
|-----------|--------|
| ~~LOCAL provider only~~ | R10A: S3-compatible read/materialize added |
| S3 output write-back | Deferred to R10B |
| No worker cache | Deferred to F4 |
| No upload/download API | Read-only (R10A: download for materialization) |

## R1 Output Closure Status (COMPLETED 2026-06-27)

- `RenderOutputRegistrationService` closes the render output → StorageReference → Product registration path
- Render output file → SHA-256 checksum → `StorageRuntimeService.register()` → `StorageRuntimeService.verifyChecksum()` → `ProductRuntimeService.register()` → `ProductRuntimeService.markReady()`
- Path traversal, zero-byte files, directory paths, missing files all rejected fail-closed
- Failed outputs registered as FAILED Products without invalid StorageReferences
- Local provider (`StorageProviderType.LOCAL`, `StorageClass.STANDARD`) remains V1 baseline
- MinIO/S3 production SDK integration deferred
- No Artifact Runtime created — Products with `RepresentationKind.MEDIA_FILE` serve as file-backed products
- No signed URLs persisted
- StorageProvider contains no pricing/billing/quota logic

## R3 Provenance Hardening Status (COMPLETED 2026-06-27)

- `RenderOutputRegistrationService.registerOutput()` now accepts optional `RenderProductProvenance`
- Storage facts (fileSize, mimeType, checksum) still populated by StorageRuntime
- Provenance metadata merged into Product metadataJson alongside storage facts
- No StorageRuntime semantic changes — storage registration flow unchanged
- No signed URLs persisted
- No absolute filesystem paths exposed in metadata

## R4 Input Materialization Status (COMPLETED 2026-06-27)

- `RenderInputMaterializationService` uses `StorageRuntimeService.materialize()` for input resolution
- Input Products registered as `RAW_MEDIA` with StorageReference backing
- Materialization validates: Product exists, READY status, MEDIA_FILE kind, storageReferenceId present,
  StorageReference exists, file exists, regular file, non-zero size, supported MIME type, safe path
- StorageRuntime owns physical materialization — render code never resolves paths directly
- No StorageRuntime semantic changes — materialization flow unchanged
- No signed URLs persisted
- No absolute filesystem paths exposed in metadata

## R5 Product Graph Dependency Edges Status (COMPLETED 2026-06-27)

- `RenderOutputRegistrationService` links formal Product dependency edges after output registration
- Dependency edges created via `ProductRuntimeService.linkDependency()` using existing infrastructure
- StorageRuntime unchanged — dependency linking is a Product lifecycle concern
- No StorageRuntime semantic changes
- No signed URLs persisted
- No absolute filesystem paths exposed in dependency metadata

## R6 TimelineRevision Render API Status (COMPLETED 2026-06-27)

- `TimelineRevisionRenderService` uses `RenderOutputRegistrationService` for output registration
- Output registered through StorageRuntime → ProductRuntime with full provenance
- StorageRuntime unchanged — output registration flow unchanged
- No StorageRuntime semantic changes
- No signed URLs persisted
- No absolute filesystem paths exposed in metadata

## R6.1 TimelineRevision Input Product Resolution Status (COMPLETED 2026-06-27)

- `TimelineInputProductResolver` resolves sourceAssetIds to inputProductIds; input materialized through `RenderInputMaterializationService` → `StorageRuntimeService.materialize()`
- FFmpeg/libass uses materialized input file (`-i <materializedPath>`) — no testsrc/lavfi fallback
- Defensive input path validation: null, exists, regular file, non-zero
- StorageRuntime unchanged — materialization flow unchanged
- No StorageRuntime semantic changes
- No signed URLs persisted
- No materialized input paths exposed in response metadata

## R10A S3-Compatible Materialization Provider Status (COMPLETED 2026-06-28)

- `S3ObjectMaterializer` (storage-module) materializes S3 objects to local temp files
- `StorageRuntimeService.materialize()` now supports both LOCAL and S3-compatible providers
- Provider detection via `StorageReference.providerType` (S3, S3_COMPATIBLE, OBJECT_STORAGE → S3 path; else LOCAL)
- S3 materialization: HeadObject → GetObject → write to temp file → SHA-256 checksum verify
- Checksum verification: compares downloaded bytes against stored `StorageReference.checksum`
- Uses existing `StorageS3Properties` configuration (`storage.s3.*`)
- AWS SDK v2 S3Client with path-style access for S3-compatible backends
- `@ConditionalOnProperty(storage.s3.enabled=true)` — only active when S3 is configured
- Integration test against RustFS dev backend (opt-in, requires `--profile s3`)
- Storage-neutral naming: no MinIO/RustFS/SeaweedFS-specific class names in new code
- No signed URLs generated or persisted
- No bucket/key exposed in public API
- Output write-back to S3 deferred to R10B

## R10A.1 S3-Backed Real Render Smoke Status (COMPLETED 2026-06-28)

- `TimelineRevisionS3RealRenderSmokeTest` proves full R6.1 + R7 chain with S3-backed input
- Input media uploaded to S3 (RustFS dev backend), StorageReference created with `S3_COMPATIBLE` provider type
- `StorageRuntimeService.materialize()` routes to `S3ObjectMaterializer` for S3-compatible provider types
- S3ObjectMaterializer downloads object to local temp file, verifies SHA-256 checksum
- FFmpeg/libass renders from materialized local path (no testsrc/lavfi)
- Output registered as LOCAL storage (S3 output write-back deferred to R10B)
- ProductDependency lineage verified: output DERIVED_FROM input
- R7 status/result queries verified: READY, resultAvailable, inputProductIds preserved
- Public response safety verified: no bucket/key/path/signed URL/provider/backend/environment exposure
- Provider type hardening: `S3_COMPATIBLE` and `OBJECT_STORAGE` added as accepted values
- `StorageProviderType` enum extended with `S3_COMPATIBLE` and `OBJECT_STORAGE` (storage-neutral naming)
- Test requires FFmpeg + S3 endpoint; skips cleanly if either unavailable
