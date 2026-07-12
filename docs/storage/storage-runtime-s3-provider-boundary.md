# StorageRuntime S3 Provider Boundary

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-RUNTIME-S3-PROVIDER-BOUNDARY.0

---

## Background

R2 support is code-backed via S3-compatible materialization. There is no independent R2StorageProvider class. S3StorageProvider is a stub. S3ObjectMaterializer is the real implementation anchor.

---

## Current Implementation Facts

| Component | Status | Role |
|-----------|--------|------|
| S3ObjectMaterializer | CODE_BACKED / R2_COMPATIBLE | Real S3/R2 materialization anchor |
| S3ClientSettingsResolver | CODE_BACKED | R2-specific client settings |
| S3CompatibilityMode | CODE_BACKED | GENERIC and R2 modes |
| StorageS3Properties | CODE_BACKED | S3/R2 config contract |
| S3StorageProvider | STUB | Architecture validation only |
| StorageRuntimeService | CODE_BACKED | Provider-neutral central service |
| StorageReference | CODE_BACKED | Internal stable reference |

---

## Responsibility Model

### StorageRuntimeService
- **Role:** Central application service boundary
- **Responsibilities:** Product/Artifact/StorageReference materialization
- **Not responsible for:** Exposing provider details, bucket inventory, remote deletion

### S3ObjectMaterializer
- **Role:** Current S3/R2 implementation anchor
- **Responsibilities:** S3-compatible object materialization
- **Uses:** AWS S3 SDK
- **Supports:** R2 via S3ClientSettingsResolver

### S3StorageProvider
- **Role:** STUB / ARCHITECTURE_VALIDATION_ONLY
- **Status:** Not full provider implementation
- **Do not treat as:** Production provider

### S3ClientSettingsResolver
- **Role:** Converts StorageS3Properties to safe S3 client settings
- **R2 behavior:** region=auto, path-style, no chunked encoding

### StorageReference
- **Role:** Internal stable reference
- **Must not leak:** Raw paths, object keys, secrets

---

## S3StorageProvider Stub Decision

**Recommendation:** KEEP_STUB_WITH_WARNING

**Rationale:**
- Preserves architecture placeholder
- Future agents may mistake it for real provider
- Add code comment warning

**Future task:** STORAGE-S3-PROVIDER-FORMALIZATION.1

---

## R2 Physical Check Attachment Point

**Status:** NOT_IN_REPORT_PATH / UNVERIFIED

**Recommended attachment:** S3ObjectMaterializer gains read-only stat/head method

**Future method shape:**
```java
StorageObjectStatus stat(StorageReference reference)
```

**Rules:**
- Read-only, HEAD/stat only
- No GET/download for existence check
- No bucket scan
- No delete
- Safe error mapping
- Redacted output

---

## Signed Access Attachment Point

**Status:** NOT_IMPLEMENTED / DEFERRED

**Recommended attachment:** Product/Artifact access service

**Future method shape:**
```java
ArtifactAccessDescriptor createAccessDescriptor(String artifactId)
```

**Rules:**
- Signed URL is access-layer only
- TTL bounded
- Not persisted as canonical reference
- Frontend consumes access API, not object key

---

## Product/Artifact Public Contract Boundary

### Allowed Public/User-facing
- productId, artifactId, renderJobId
- status, mimeType, filename
- duration/resolution/fps
- Access endpoint URL
- Short-lived signed URL (access response only)

### Not Allowed
- Bucket, object key
- R2 account ID, S3 endpoint
- Access key, secret key
- Raw local path
- Signed URL as persisted canonical metadata

---

## What Future Agents Must Not Assume

| Assumption | Reality |
|------------|---------|
| S3StorageProvider is full provider | STUB only |
| Independent R2StorageProvider required | NOT_REQUIRED |
| R2 HEAD/stat in physical report | NOT_IN_REPORT_PATH |
| Signed access exists | NOT_IMPLEMENTED |
| OpenDAL needed | DEFERRED |

---

## Recommended Next Tasks

1. STORAGE-R2-PHYSICAL-CHECK.0
2. STORAGE-R2-SIGNED-ACCESS.1
3. FRONTEND-USER-RENDER-RESULT-DETAIL.1
4. STORAGE-R2-PREVIEW-SMOKE-CI.1

---

## Status

- STORAGE-RUNTIME-S3-PROVIDER-BOUNDARY.0: COMPLETE
- Responsibility model: DOCUMENTED
- Attachment points: IDENTIFIED
