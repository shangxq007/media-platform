# StorageRuntime Provider Matrix

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-RUNTIME-PROVIDER-MATRIX.0

---

## Provider Vocabulary

### Capabilities

| Capability | Description |
|------------|-------------|
| write | Store object |
| read | Read/open object |
| materialize | Local temp file for worker |
| stat/head | Check existence/metadata |
| signed access | Presigned URL generation |
| physical check | Report-only existence check |
| metadata read | Size/mime/checksum |
| cleanup | Worker temp cleanup |

### Status Values

| Status | Description |
|--------|-------------|
| COMPLETE | Fully implemented |
| PREVIEW_READY | Works in preview |
| PARTIAL | Partially implemented |
| UNSUPPORTED | Provider does not support |
| DEFERRED | Future work |
| NOT_APPLICABLE | Not relevant |

### Readiness Levels

| Level | Description |
|-------|-------------|
| LOCAL_DEV_READY | Local development |
| PREVIEW_READY | Preview deployment |
| PRODUCTION_CANDIDATE | Ready for production evaluation |
| PRODUCTION_READY | Production approved |

---

## Provider Matrix

| Capability | Local | R2 | S3-compatible | OpenDAL |
|------------|-------|-----|---------------|---------|
| Status | LOCAL_DEV_READY | PREVIEW_READY | DEFERRED | EVALUATED_GO_WITH_LIMITS |
| write | ✅ COMPLETE | ✅ COMPLETE | — | — |
| read | ✅ COMPLETE | ✅ COMPLETE | — | — |
| materialize | ✅ COMPLETE | ✅ COMPLETE | — | — |
| stat/head | ✅ COMPLETE | ✅ COMPLETE | — | — |
| signed access | N/A | DEFERRED | — | — |
| physical check | ✅ COMPLETE | ✅ COMPLETE | — | — |
| metadata | ✅ COMPLETE | PARTIAL | — | — |
| cleanup | ✅ COMPLETE | N/A | — | — |
| destructive ops | ❌ NO | ❌ NO | ❌ NO | ❌ NO |
| bucket scan | ❌ NO | ❌ NO | ❌ NO | ❌ NO |

---

## Readiness Assessment

### Local Provider

**Readiness:** LOCAL_DEV_READY

**Capabilities:**
- Write/read/materialize ✅
- Stat/head ✅
- Physical check ✅
- Path traversal protection ✅
- Symlink protection ✅
- Cleanup compatible ✅

**Limitations:**
- Not remote durable
- Requires mounted volume for multi-host

---

### R2 Provider

**Readiness:** PREVIEW_READY

**Capabilities:**
- Write/read/materialize ✅
- Stat/head ✅
- Physical check ✅
- R2 compatibility mode ✅
- Object key namespace ✅

**Limitations:**
- Signed access deferred
- Production readiness not yet claimed
- Metadata partial

---

### S3-compatible (Future)

**Readiness:** DEFERRED

**Notes:**
- May share R2/S3 implementation
- Not separate provider unless implemented
- Future candidate

---

### OpenDAL Adapter

**Readiness:** EVALUATED_GO_WITH_LIMITS (updated 2026-07-15)

**Evaluation:** [storage-opendal-evaluation.md](storage-opendal-evaluation.md)

**Facts:**
- `opendal-java:0.46.4` declared in storage-module/build.gradle.kts
- `StorageProviderType.OPENDAL` exists in delivery contract enum
- `LAB_OPENDAL_FS_INTERNAL` delivery profile exists (EXPERIMENTAL, disabled)
- Current `OpenDalMaterializer` is NIO shim — does NOT use OpenDAL API
- Zero imports of `org.apache.opendal` in codebase
- Docker image (glibc/Jammy) compatible with native library
- Presigned URLs NOT supported by OpenDAL — hybrid approach needed

**Allowed next:** Docker native validation + BlobStorage SPI implementation
**Forbidden:** R2 replacement, presign via OpenDAL, production activation

---

## Stable Provider Contract

| Rule | Status |
|------|--------|
| Provider behind StorageRuntime | ✅ |
| storageReferenceId canonical | ✅ |
| Signed URLs access-layer only | ✅ |
| No provider details in public API | ✅ |
| No secrets exposed | ✅ |
| No destructive operations | ✅ |

---

## Storage Roadmap

### Immediate

- FRONTEND-USER-RENDER-RESULT-DETAIL.1
- STORAGE-R2-SIGNED-ACCESS.1

### Near-term

- STORAGE-R2-PREVIEW-SMOKE-CI.1
- STORAGE-RUNTIME-PROVIDER-CAPABILITIES.1
- STORAGE-RUNTIME-PROVIDER-ERROR-MAPPING.1

### Later

- STORAGE-RUNTIME-ORPHAN-CLEANUP.1_LATER
- STORAGE-RUNTIME-S3-COMPATIBLE.1
- STORAGE-OPENDAL-ADAPTER.0_LATER → EVALUATED (see storage-opendal-evaluation.md)

### Not Now

- OpenDAL adapter
- Full storage GC
- Remote delete
- Bucket scan
- OpenAssetIO

---

## Guardrails

| Guardrail | Status |
|-----------|--------|
| OpenCue NOT STARTED | ✅ |
| Artifact DAG POSTPONED | ✅ |
| OpenDAL EVALUATED_GO_WITH_LIMITS | ✅ |
| OpenAssetIO DEFERRED | ✅ |
| Merge MERGE_EXPERIMENTAL | ✅ |
| Branch/Patch/ANTLR/CRDT NOT INTRODUCED | ✅ |

---

## Status

- STORAGE-RUNTIME-PROVIDER-MATRIX.0: COMPLETE
- Provider matrix: DOCUMENTED
- Readiness levels: DEFINED
- Storage roadmap: CREATED
