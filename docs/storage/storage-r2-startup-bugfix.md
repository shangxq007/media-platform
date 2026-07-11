# Storage R2 Startup Bugfix

**Date:** 2026-07-11
**Status:** COMPLETE
**Authority:** STORAGE-R2-STARTUP-BUGFIX.1

---

## Root Cause

S3BlobStorageProvider and S3ObjectMaterializer eagerly built S3Client/S3Presigner in constructors. AWS SDK v2 client builder performs HTTP client initialization (DNS resolution, connection pooling) during `build()`, causing startup hang when R2 endpoint is configured.

---

## Fix

| Component | Change |
|-----------|--------|
| S3BlobStorageProvider | Lazy S3Client + S3Presigner via double-checked locking |
| S3ObjectMaterializer | Lazy S3Client via double-checked locking |

### S3BlobStorageProvider
- Fields: `volatile S3Client`, `volatile S3Presigner`
- Constructor: logs settings only, no `buildClient()`/`buildPresigner()`
- Added: `getClient()`/`getPresigner()` with synchronized double-checked locking

### S3ObjectMaterializer
- Field: `volatile S3Client`
- Constructor: logs settings only, no `buildClient()`
- Added: `getClient()` with synchronized double-checked locking

---

## Verification

| Check | Status |
|-------|--------|
| Compile | ✅ |
| Storage module tests | ✅ |
| Constructor has no buildClient calls | ✅ |
| Fields are volatile | ✅ |
| Lazy accessors use double-checked locking | ✅ |
| No direct s3Client.X in method bodies | ✅ |

---

## Status

- STORAGE-R2-STARTUP-BUGFIX.1: COMPLETE
- Root cause: Eager S3Client initialization in constructors
- Fix: Lazy initialization with double-checked locking
