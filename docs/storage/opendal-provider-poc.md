# OpenDAL Provider POC

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-OPENDAL-PROVIDER-POC.0

---

## Context

OpenDAL Evaluation: GO_WITH_LIMITS. Current R2 path: KEEP_STABLE. OpenDAL for non-S3 backends first.

---

## Implementation

| Component | Status |
|-----------|--------|
| OpenDAL dependency | ✅ `org.apache.opendal:opendal-java:0.46.4` |
| OpenDalExperimentalProperties | ✅ CREATED |
| OpenDalMaterializer | ✅ CREATED |
| Local fs smoke test | ✅ PASSED |

---

## Configuration

```yaml
storage:
  experimental:
    opendal:
      enabled: false  # disabled by default
      backend: fs
      root: /tmp/media-platform-opendal-lab
      mode: poc
```

---

## Local Filesystem Smoke

| Operation | Result |
|-----------|--------|
| Write | ✅ PASSED |
| Read | ✅ PASSED |
| Exists | ✅ PASSED |
| Size | ✅ PASSED |
| Missing key | ✅ Returns empty |

---

## R2 Path Safety

| Check | Result |
|-------|--------|
| S3ObjectMaterializer unchanged | ✅ |
| S3BlobStorageProvider unchanged | ✅ |
| AccessDescriptor unchanged | ✅ |
| Signed URL unchanged | ✅ |
| OpenDAL disabled by default | ✅ |

---

## Status

- STORAGE-OPENDAL-PROVIDER-POC.0: COMPLETE
- OpenDAL status: EXPERIMENTAL_PROVIDER_POC
- Production path: DEFERRED
- Current R2 path: KEEP_STABLE
