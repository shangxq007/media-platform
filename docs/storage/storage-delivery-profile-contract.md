# Storage Delivery Profile Contract

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-DELIVERY-PROFILE-CONTRACT.0

---

## Context

Storage Delivery Profile Design complete. R2 signed access authz complete. Current R2 preview path verified and protected. OpenDAL is experimental. Runtime profile switching is not implemented.

---

## Goals

- Define profile config contract
- Define profile IDs
- Define provider/backend/access mode mapping
- Define capability model
- Define lifecycle
- Define validation rules
- Define AccessDescriptor relationship
- Preserve current R2 behavior

## Non-goals

- No runtime switching
- No provider switch
- No schema migration
- No signed URL behavior change
- No OpenDAL/RustFS production activation

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Storage Delivery Profile** | Named config bundle for how Artifact is stored/accessed/delivered |
| **Storage Provider** | Implementation adapter (S3-compatible, OpenDAL, local fs) |
| **Storage Backend** | Concrete backend (R2, AWS S3, RustFS, MinIO, local fs) |
| **Access Mode** | How consumer obtains content (signed URL, internal stream, etc.) |
| **Delivery Capability** | Profile-supported behavior (presign, stream, write, read) |
| **Profile Status** | VERIFIED, PREVIEW_VERIFIED, EXPERIMENTAL, LAB_ONLY, DESIGN_ONLY |
| **Runtime Activation** | Whether profile can be selected by runtime |
| **Default Profile** | Profile used when no explicit selection; current = preview-r2-signed-url |

---

## Profile IDs

| Profile ID | Status | Description |
|-----------|--------|-------------|
| preview-r2-signed-url | PREVIEW_VERIFIED | Current verified R2 signed URL path |
| lab-opendal-fs-internal | EXPERIMENTAL | OpenDAL local filesystem lab |
| lab-rustfs-s3-signed-url | LAB_ONLY | RustFS S3-compatible lab |
| lab-minio-s3-signed-url | DESIGN_ONLY | MinIO S3-compatible lab |
| customer-owned-s3-external-bucket | DESIGN_ONLY | Future customer storage |
| export-bundle-r2 | DESIGN_ONLY | Future export bundle |
| private-render-artifacts-r2 | DESIGN_ONLY | Future private artifacts |
| internal-cache-local | DESIGN_ONLY | Future internal cache |

---

## Configuration Shape

```yaml
storage:
  delivery:
    default-profile: preview-r2-signed-url
    profiles:
      preview-r2-signed-url:
        status: PREVIEW_VERIFIED
        enabled: true
        runtimeSelectable: true
        provider: s3-compatible
        backend: r2
        accessMode: SIGNED_URL
        capabilities:
          writeArtifact: true
          readArtifact: true
          presignRead: true
          internalStream: false
          externalBucket: false
          tenantScopedAccess: true
          projectScopedAccess: true
        security:
          exposeStorageReference: false
          exposeBucket: false
          exposeObjectKey: false
          persistSignedUrl: false

      lab-opendal-fs-internal:
        status: EXPERIMENTAL
        enabled: false
        runtimeSelectable: false
        provider: opendal
        backend: local-fs
        accessMode: INTERNAL_STREAM
```

---

## Capability Model

| Capability | Description |
|-----------|-------------|
| writeArtifact | Can write artifact objects |
| readArtifact | Can read artifact objects |
| presignRead | Can generate presigned GET URLs |
| internalStream | Can stream through API |
| externalBucket | Can hand off to external bucket |
| exportBundle | Can generate export packages |
| deleteArtifact | Can delete artifacts |
| tenantScopedAccess | Requires tenant authorization |
| projectScopedAccess | Requires project authorization |

---

## Access Mode Contract

| Mode | Current Status | Required Capability | User Visibility |
|------|---------------|---------------------|-----------------|
| SIGNED_URL | VERIFIED | presignRead | Signed URL only |
| INTERNAL_STREAM | DESIGN | internalStream | API stream |
| LOCAL_PATH | LAB_ONLY | internal file | Never user-facing |
| DIRECT_COPY | DESIGN_ONLY | backend copy | None/admin |
| EXTERNAL_BUCKET | DESIGN_ONLY | external bucket | Safe descriptor |
| EXPORT_BUNDLE | DESIGN_ONLY | export bundle | Download link |
| NO_PUBLIC_ACCESS | DESIGN_ONLY | none | No access |

---

## Lifecycle States

```
DESIGN_ONLY → LAB_ONLY → EXPERIMENTAL → PREVIEW_VERIFIED → VERIFIED → DEPRECATED → DISABLED
```

**Rules:**
- Only PREVIEW_VERIFIED/VERIFIED may be runtimeSelectable
- LAB_ONLY cannot be used by production/preview runtime
- Profile promotion requires tests and docs

---

## Current Default Behavior

**Default profile:** `preview-r2-signed-url`

**Current flow:**
```
Artifact → StorageReference → R2/S3 object
  → tenant/project scoped access endpoint
  → authorization check
  → signed URL generated on demand
  → AccessDescriptor returned
```

---

## Validation Rules

- Default profile must exist
- runtimeSelectable profile must be enabled
- SIGNED_URL profile must support presignRead
- LAB/DESIGN profiles cannot be runtimeSelectable
- persistSignedUrl must be false
- Credentials must not appear in profile config
- No remote network calls in validation

---

## Security Rules

| Field | User Visibility |
|-------|----------------|
| bucket | Never |
| objectKey | Never |
| storageReferenceId | Never |
| local path | Never |
| signed URL | Generated on demand only, never persisted |
| credentials | Never |

---

## OpenDAL / RustFS / MinIO

| Provider | Status | Profile | RuntimeSelectable |
|----------|--------|---------|-------------------|
| OpenDAL | EXPERIMENTAL | lab-opendal-fs-internal | false |
| RustFS | LAB_ONLY | lab-rustfs-s3-signed-url | false |
| MinIO | DESIGN_ONLY | lab-minio-s3-signed-url | false |

---

## Current Decisions

| Decision | Status |
|----------|--------|
| Default profile | preview-r2-signed-url |
| R2 behavior | UNCHANGED |
| Runtime switching | NOT_IMPLEMENTED |
| OpenDAL | EXPERIMENTAL, disabled |
| RustFS | LAB_ONLY |
| Signed URLs | Generated on demand, not persisted |

---

## Follow-up Tasks

1. STORAGE-DELIVERY-PROFILE-DTO.0
2. STORAGE-DELIVERY-PROFILE-CONFIG-BINDING.0
3. STORAGE-DELIVERY-PROFILE-VALIDATOR.0

---

## Status

- STORAGE-DELIVERY-PROFILE-CONTRACT.0: COMPLETE
- Runtime switching: NOT_IMPLEMENTED
- Current R2 path: KEEP_STABLE
