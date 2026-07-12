# Storage R2 Startup Bugfix

**Date:** 2026-07-11
**Status:** COMPLETE — Phase 2 (S3ObjectWriter fix)
**Authority:** STORAGE-R2-STARTUP-BUGFIX.2

---

## Root Cause (Updated)

All S3-compatible beans eagerly built S3Client/S3Presigner in constructors. AWS SDK v2 client builder performs HTTP client initialization (DNS resolution, connection pooling) during `build()`, causing startup hang when R2 endpoint is configured.

**Phase 1 (commit 435cd0d):** Fixed S3BlobStorageProvider and S3ObjectMaterializer.
**Phase 2:** S3ObjectWriter was missed — still eagerly built S3Client. This is the remaining root cause.

---

## Fix

| Component | Change |
|-----------|--------|
| S3BlobStorageProvider | Phase 1: Lazy S3Client + S3Presigner via double-checked locking |
| S3ObjectMaterializer | Phase 1: Lazy S3Client via double-checked locking |
| S3ObjectWriter | Phase 2: Lazy S3Client via double-checked locking |

### S3BlobStorageProvider (Phase 1)
- Fields: `volatile S3Client`, `volatile S3Presigner`
- Constructor: logs settings only, no `buildClient()`/`buildPresigner()`
- Added: `getClient()`/`getPresigner()` with synchronized double-checked locking

### S3ObjectMaterializer (Phase 1)
- Field: `volatile S3Client`
- Constructor: logs settings only, no `buildClient()`
- Added: `getClient()` with synchronized double-checked locking

### S3ObjectWriter (Phase 2)
- Field: `volatile S3Client`
- Constructor: logs settings only, no `buildClient()`
- Added: `getClient()` with synchronized double-checked locking
- All `s3Client.X()` calls replaced with `getClient().X()`

---

## Verification

| Check | Phase 1 | Phase 2 |
|-------|---------|---------|
| Compile | ✅ | ✅ |
| Storage module tests | ✅ | ✅ |
| Constructor has no buildClient calls | ✅ | ✅ |
| Fields are volatile | ✅ | ✅ |
| Lazy accessors use double-checked locking | ✅ | ✅ |
| No direct s3Client.X in method bodies | ✅ | ✅ |

---

## Diagnostic Tooling

Added `StartupDiagnosticsListener` (platform-app) to trace startup lifecycle events with timestamps.

To use:
1. Set `logging.level.com.example.platform.app.StartupDiagnosticsListener=DEBUG`
2. Run with `--debug` flag for full condition evaluation report
3. If startup still hangs, get a thread dump:
   - `kill -3 <pid>` (prints to stdout)
   - `jstack <pid>` (prints to terminal)
   - `jcmd <pid> Thread.print` (prints to terminal)

---

## Remaining Risk Areas

If startup still hangs after this fix, investigate:

1. **ApplicationReadyEvent listeners** — Some listener after FeatureFlagStartupHydrator may trigger S3 operations
2. **@Scheduled tasks** — Tasks with default initialDelay=0 run immediately; if they use BlobStorage and the S3 endpoint is unreachable, the scheduler thread pool could be exhausted
3. **Bean initialization ordering** — Some transitive dependency might trigger S3Client creation during context refresh despite lazy fields
4. **AWS SDK v2 background threads** — The SDK may create I/O threads that attempt DNS resolution independently

---

## Status

- STORAGE-R2-STARTUP-BUGFIX.1: COMPLETE (S3BlobStorageProvider + S3ObjectMaterializer)
- STORAGE-R2-STARTUP-BUGFIX.2: COMPLETE (S3ObjectWriter)
- Root cause: Eager S3Client initialization in constructors
- Fix: Lazy initialization with double-checked locking
