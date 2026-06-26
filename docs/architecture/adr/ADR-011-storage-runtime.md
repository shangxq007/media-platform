---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-011: Storage Runtime

## Context

Product Runtime (A4.1) references storage via StorageReference but does not own physical data operations. ExecutionBackend currently uses ProcessBuilder with local file paths — there is no storage abstraction for object stores, caching, or streaming.

## Decision

Introduce a Storage Runtime as the platform's physical data access layer:

1. `StorageReference` identifies data (provider, bucket, key, checksum, contentHash)
2. `StorageAccess` authorizes access (signed URL, expiration, access mode) — never persisted
3. `StorageRuntime.materialize(storageRef)` returns local path for execution
4. `StorageRuntime.openRead(storageRef)` returns InputStream for streaming
5. Worker cache managed by Storage Runtime (contentHash-based, LRU with TTL)
6. ExecutionBackend never downloads files directly

Object storage providers (S3, OSS, GCS, MinIO, local FS) are abstracted internally.

## Consequences

- ExecutionBackend becomes storage-agnostic
- Workers share cache via contentHash
- Signed URLs generated on demand (not persisted)
- OpenCue/BMF workers use Storage Runtime for file access

## Rejected Alternatives

1. ExecutionBackend manages storage directly: couples execution to storage implementation
2. Signed URLs persisted in Product table: security risk, expiration issues
3. Each provider implements its own storage: no unified caching, no consistent checksum verification

## Migration

Phase 1: StorageReference + StorageRuntime API
Phase 2: Worker cache integration
Phase 3: S3/MinIO/GCS provider implementations
