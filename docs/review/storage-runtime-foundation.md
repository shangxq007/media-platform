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
| `StorageProviderType` | LOCAL, MINIO, S3, OSS, GCS, AZURE |
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
- V1: LOCAL provider only (MINIO/S3/OSS/GCS reserved)
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
| LOCAL provider only | MINIO/S3 deferred |
| No worker cache | Deferred to F4 |
| No upload/download API | Read-only |
