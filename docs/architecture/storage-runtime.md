---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Storage Runtime & Data Access Blueprint

> **Linked ADR:** [ADR-011](adr/ADR-011-storage-runtime.md)
> **Predecessors:** [Product Runtime](product-runtime.md) (A4.1), [Execution Planner](execution-planner.md) (A2)

## 1. Responsibilities

| Storage Runtime OWNS | Storage Runtime does NOT own |
|---------------------|---------------------------|
| StorageReference | Product metadata |
| Object storage abstraction | Execution planning |
| Upload / Download / Delete | Timeline / Review |
| Copy / Move | Provider logic |
| Checksum verification | Semantic metadata |
| Content hash | Search projection |
| Worker cache | Marketplace listing |
| Streaming / Range Read | Asset governance |
| Multipart upload | AI enrichment |
| Presigned URL generation | |
| Access policy | |

## 2. StorageReference

Identifies data — NOT authorization.

```
StorageReference {
    storageId: String
    provider: String (s3, oss, gcs, minio, local)
    bucket: String
    objectKey: String
    versionId: String (optional)
    checksum: String (SHA-256)
    contentHash: String (for cache key)
    size: long
    mimeType: String
    etag: String (optional)
    storageClass: STANDARD | GLACIER | ARCHIVE
    createdAt: Instant
}
```

## 3. StorageAccess

Authorizes access — NOT identity.

```
StorageAccess {
    readUrl: String (signed, time-limited)
    writeUrl: String (signed, time-limited)
    expiration: Instant
    accessMode: READ | WRITE | READ_WRITE
    credentialReference: String (optional)
}
```

**NEVER persist signed URLs.** Generate on demand with short expiration.

## 4. AccessStrategy

| Strategy | Description | Status |
|----------|-------------|--------|
| LOCAL_CACHE | Download to worker-local cache before execution | ✅ V1 |
| REMOTE_STREAM | Stream directly from object store | Reserved |
| RANGE_READ | HTTP Range requests for partial data | Reserved |
| MULTIPART_UPLOAD | Parallel chunked upload | Reserved |
| DIRECT_OBJECT_ACCESS | Worker directly accesses object store | Reserved |
| HYBRID | Smart selection based on size/bandwidth | Reserved |

## 5. Worker Cache

```
CacheKey: contentHash
CacheRoot: /tmp/platform-cache/{tenantId}/{projectId}/
CachePolicy: LRU with TTL
Eviction: Least-recently-used + TTL expired
Validation: contentHash match on access
```

ExecutionBackend requests `StorageRuntime.materialize(storageRef, accessStrategy)` → returns local path.

## 6. Object Storage Providers

Storage Runtime abstracts: Local FS, MinIO, S3, OSS, GCS, Azure Blob. Future: Ceph, NAS, NFS.

Provider-specific logic is internal to Storage Runtime — never leaks to ExecutionBackend or Product Runtime.

## 7. Streaming

- Stream access for sequential media processing (transcoding)
- Range access for partial reads (thumbnail from frame offset)
- Zero-copy when source and destination share filesystem
- Temporary staging for multipart assembly

ExecutionBackend decides: stream or materialize.

## 8. Materialization

```
MaterializationPolicy:
  ON_DEMAND — fetch when needed
  EAGER — prefetch before execution
  CACHE_ONLY — fail if not cached
  STREAM_ONLY — fail if can't stream
```

ExecutionBackend specifies policy in ExecutionRequest.

## 9. Checksum vs ContentHash

| Checksum | ContentHash |
|----------|------------|
| Transport validation (SHA-256) | Logical identity |
| Verifies download integrity | Cache key for worker |
| Computed from file bytes | Computed from Product inputs |
| Changes on corruption | Changes when inputs change |

## 10. Storage Lifecycle

| State | Description |
|-------|-------------|
| UPLOADING | In transit to object store |
| AVAILABLE | Ready for download |
| MATERIALIZED | Local copy on worker |
| CACHED | In worker cache |
| ARCHIVED | Moved to cold storage |
| DELETED | Storage freed |
| FAILED | Upload/download failed |

## 11. ExecutionBackend Interaction

ExecutionBackend NEVER downloads files directly.

```
ExecutionBackend → StorageRuntime.materialize(storageRef) → local path
ExecutionBackend → StorageRuntime.openRead(storageRef) → InputStream
```

Storage Runtime decides: cache, stream, or download.

## 12. OpenCue Interaction

```
OpenCue Worker receives StorageReference
    → StorageRuntime.materialize(storageRef) → local path
    → Execute BMF/FFmpeg
    → StorageRuntime.upload(resultPath) → new StorageReference
    → Report result
```

No signed URL logic inside workers.

## 13. BMF Interaction

```
BMF receives local path or InputStream from Storage Runtime.
BMF does NOT understand object storage.
Storage Runtime handles all object store access.
```

## 14. PostgreSQL Decision

PostgreSQL stores `StorageReference` (metadata) only. NEVER stores large media files as blobs. Large files always live in object storage.
