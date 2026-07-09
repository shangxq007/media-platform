# Storage Runtime Contract

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-RUNTIME-CONTRACT.0

---

## storageReferenceId Semantics

### Valid Meanings

| Meaning | Example |
|---------|---------|
| Internal stable reference | `stor_xxx` |
| Resolvable by StorageRuntime | YES |
| Provider-neutral | YES |
| Tenant/project scoped | Via Product/Artifact |

### Invalid Meanings

| Invalid | Reason |
|---------|--------|
| Raw local path | Not provider-neutral |
| Signed URL | Temporary, not stable |
| Public URL | Security risk |
| Product id | Different identity |
| Artifact id | Different identity |
| Materialized temp path | Ephemeral |

---

## RAW_MEDIA Product Input Contract

| Rule | Status |
|------|--------|
| type = RAW_MEDIA | ✅ |
| status = READY | ✅ |
| storageReferenceId required | ✅ |
| tenant/project scope | ✅ |
| mimeType present | ✅ |

**Product-backed resolver validates:**
- Product exists
- Same tenant/project
- Type RAW_MEDIA
- Status READY
- storageReferenceId exists
- StorageRuntime can materialize

---

## Output Product/Artifact Contract

| Rule | Status |
|------|--------|
| RenderJob COMPLETED requires output | ✅ |
| Output Product READY requires stored content | ✅ |
| Artifact content accessible | ✅ |
| No raw local paths in public API | ✅ |
| No signed URLs as canonical refs | ✅ |

---

## Materialization Contract

```
storageReferenceId
  → StorageRuntime.resolve()
  → worker-local safe input handle
  → FFmpeg input
  → cleanup
```

**Rules:**
- Materialized files are ephemeral
- Public responses must not reveal materialized paths
- Cleanup follows StorageRuntime policy

---

## Worker Storage Access Contract

| Requirement | Status |
|-------------|--------|
| Database access | ✅ |
| Storage backend config | ✅ |
| Materialization temp dir | ✅ |
| FFmpeg binary | ✅ |
| libass/fonts | ✅ |
| No hardcoded paths | ✅ |
| No secrets in logs | ✅ |

---

## Provider Backend Boundary

### Local Backend

- Stores bytes on local filesystem
- storageReferenceId ≠ raw path in public contract
- Worker needs same local storage or mounted volume

### R2/S3 Backend

- Stores bytes in object storage
- storageReferenceId maps to bucket/key
- Worker needs credentials/config
- Signed URLs are not canonical storageReferenceId

### Future OpenDAL Adapter

- OpenDAL NOT introduced
- May become StorageRuntime provider adapter
- Platform models must not depend on OpenDAL types

---

## Public API Safety Rules

| Rule | Status |
|------|--------|
| Raw local paths never in public API | ✅ |
| Secrets never in docs/logs/responses | ✅ |
| Signed URLs not canonical refs | ✅ |
| Generated FFmpeg not canonical | ✅ |
| Generated ASS not canonical | ✅ |
| Materialized files cleaned up | ✅ |
| Safe failure messages | ✅ |

---

## Failure Behavior

### Input Failures

| Failure | Behavior |
|---------|----------|
| Product missing | SAFE_FAILURE |
| Wrong tenant/project | SAFE_FAILURE |
| Wrong type | SAFE_FAILURE |
| Not READY | SAFE_FAILURE |
| Missing storageReferenceId | SAFE_FAILURE |
| Storage object missing | SAFE_FAILURE |
| Materialization failure | SAFE_FAILURE |
| URI fallback unavailable | SAFE_FAILURE |

### Output Failures

| Failure | Behavior |
|---------|----------|
| FFmpeg success + storage fail | RenderJob FAILED |
| Product creation fail | RenderJob FAILED |
| Artifact creation fail | RenderJob FAILED |
| Content inaccessible | No READY output |

---

## Status

- STORAGE-RUNTIME-CONTRACT.0: COMPLETE
- Contract formalized: YES
- OpenDAL: NOT INTRODUCED (deferred)
- OpenCue: NOT STARTED
