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

### StorageReference Locator Semantics

`StorageReference` is the **internal locator** for platform-controlled StorageRuntime objects.
It must not be treated as a universal external storage descriptor.

#### Internal Interpretation by Provider Type

```text
providerType = LOCAL
  rootPath      = internal storage root / mounted volume root
  relativePath  = relative file path under rootPath

providerType = S3 / S3_COMPATIBLE / OBJECT_STORAGE
  rootPath      = internal bucket name
  relativePath  = object key
```

#### Key Constraints

- `rootPath` and `relativePath` are **internal locator fields**, not public API contract fields.
- They must **not** be exposed in Product result responses, render status responses, public timeline responses, or frontend contracts.
- They must **not** be used for external user-owned storage delivery targets by default.
- They must **not** be confused with materialized local paths.
- `absolutePath()` concatenates `rootPath + "/" + relativePath` for LOCAL provider; for S3-compatible, it produces `bucket/key` (internal use only).

#### Materialization Semantics

Materialization means:

```text
StorageReference
→ StorageRuntime.materialize()
→ local temporary execution file/path
```

For LOCAL:

```text
rootPath + relativePath
→ validated local file
```

For S3-compatible:

```text
bucket + object key
→ HeadObject / GetObject
→ local temp file
→ checksum verification
```

- The materialized local path is **runtime-only**.
- The materialized local path must **not** be persisted as canonical Product storage.
- The materialized local path must **not** be exposed through public APIs.
- The materialized local path can be used internally by FFmpeg/libass and workers.

#### Checksum and Size Semantics

- StorageReference carries checksum, fileSize, and mimeType where available.
- S3-compatible materialization must verify checksum when expected checksum is available.
- Output registration must compute checksum after render output is complete.
- R10B should verify checksum after upload or after object registration.
- ETag must **not** be assumed to be SHA-256, especially for multipart uploads or non-AWS S3-compatible backends.

> **Checksum is the platform integrity signal. ETag is backend metadata and must not be treated as canonical checksum unless explicitly validated.**

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
| ~~S3 output write-back~~ | R10B: S3-compatible internal output write-back added |
| No worker cache | Deferred to F4 |
| No upload/download API | Read-only (R10A: download for materialization, R10B: upload for output) |

## R10B Output Key Strategy Reservation

### Recommended Initial Internal Object Key

```text
projects/{projectId}/render-jobs/{renderJobId}/outputs/final.mp4
```

Alternative future strategy (with tenant isolation):

```text
tenants/{tenantId}/projects/{projectId}/render-jobs/{renderJobId}/outputs/final.mp4
```

### Constraints

- R10B should initially write internal render output to the configured platform-controlled S3-compatible bucket.
- The bucket is configured as **internal platform storage**.
- The key is **internal** and must **not** be exposed publicly.
- Product metadata may reference output Product and render job, but must **not** leak bucket/key.
- Public result APIs should return safe Product/render metadata only.
- Download/delivery should be separate future capabilities.

### Recommended Temp Object / Failure Cleanup Strategy

Preferred future-safe design:

```text
tmp/render-jobs/{renderJobId}/{uuid}.mp4
→ upload
→ checksum verify
→ copy/promote to final key
→ delete temp key
→ register StorageReference
→ Product READY
```

For a minimal first implementation, direct upload to final key may be acceptable only if:

- Upload failures delete partial objects when possible.
- Failed output Product is **not** marked READY.
- ProductDependency is **not** linked for failed output.
- Failure does **not** expose bucket/key/path/command.
- Final key is deterministic and idempotency behavior is documented.

### Transaction and Consistency Boundary

R10B must treat database state and object storage state carefully.

Recommended success order:

```text
render local temp output
→ compute checksum
→ upload to S3-compatible internal storage
→ verify object exists / checksum
→ register StorageReference
→ register Product
→ mark Product READY
→ link ProductDependency
```

Failure rules:

- If upload fails, no READY Product.
- If checksum verification fails, delete uploaded object if possible and no READY Product.
- If DB registration fails after upload, object may become orphaned and should be eligible for future sweeper cleanup.
- No public API should expose partially uploaded objects.
- R10B may document a future orphan-object sweeper.

### R10B Future Output Write-Back Flow

```text
FFmpeg local temp output
→ checksum
→ upload to internal S3-compatible bucket/key
→ verify
→ StorageReference(providerType=S3_COMPATIBLE)
→ FINAL_RENDER Product READY
```

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

## R10B S3-Compatible Internal Output Write-Back Status (COMPLETED 2026-06-28)

- `S3ObjectWriter` (storage-module) uploads local render output files to S3-compatible internal storage
- `RenderOutputRegistrationService` now supports both LOCAL and S3-compatible output registration
- Output provider selection via `RenderOutputStorageProperties` (`storage.output.provider=local|s3-compatible`)
- S3 output flow: render local temp → compute checksum → upload to S3 → verify → register StorageReference → Product READY
- StorageReference created with `S3_COMPATIBLE` provider type, rootPath=bucket, relativePath=object key
- Object key strategy: `{prefix}/{projectId}/render-jobs/{renderJobId}/outputs/{filename}`
- Checksum verification: local SHA-256 compared against uploaded object (ETag not trusted as SHA-256)
- Failure cleanup: partial uploads deleted on checksum mismatch or registration failure
- Direct upload to final key (temp→promote deferred as future hardening)
- Orphan object sweeper documented as future hardening
- ProductDependency lineage linked after successful S3 output registration
- R7 status/result APIs remain safe: no bucket/key/path/signed URL exposure
- Integration smoke test: `TimelineRevisionS3OutputRealRenderSmokeTest` (opt-in, requires FFmpeg + S3 endpoint)
- Storage-neutral naming: no MinIO/RustFS/SeaweedFS-specific class names in new code

## External Channel Extension Boundary

See [External Channel Extension Model](../architecture/blueprint/external-channel-extension-model.md) for the reserved extension points clarifying how external input/output channels (ClientPush, ExternalStorageConnection, IngestSource, WatchSource, DeliveryTarget) relate to StorageRuntime. External channel concepts are outside StorageRuntime and do not replace Product canonical storage by default.
