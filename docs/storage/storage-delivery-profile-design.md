# Storage Delivery Profile Design

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-DELIVERY-PROFILE-DESIGN.0

---

## Context

Preview baseline merged to main. R2 path verified. Integration Lab complete. OpenDAL POC complete. RustFS lab created. Future delivery complexity expected.

---

## Goals

- Define storage/delivery profile vocabulary
- Support future backend flexibility
- Protect current R2 path
- Keep AccessDescriptor stable
- Keep StorageReference internal
- Avoid premature runtime switching

## Non-goals

- No runtime implementation
- No R2 migration
- No OpenDAL production adoption
- No schema changes

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Storage Provider** | Technical implementation that can write/read/stat objects |
| **Storage Backend** | Actual storage service (R2, RustFS, local fs, etc.) |
| **Delivery Profile** | Platform config describing where artifacts are delivered and how accessed |
| **Access Mode** | How users/systems receive artifact content |
| **StorageReference** | Internal stable reference to materialized object |
| **AccessDescriptor** | User-facing, generated-on-demand access description |

---

## Relationship Model

```
Artifact
  → StorageReference
    → Storage Provider
      → Storage Backend
  → AccessDescriptor
    → Access Mode
    → Delivery Profile
```

---

## Initial Delivery Profiles

| Profile | Provider | Backend | Access Mode | Status |
|---------|----------|---------|-------------|--------|
| preview-r2 | S3/R2 | Cloudflare R2 | SIGNED_URL | PREVIEW_VERIFIED |
| lab-opendal-fs | OpenDAL | local fs | INTERNAL_ONLY | EXPERIMENTAL |
| lab-rustfs-s3 | OpenDAL | RustFS | INTERNAL_ONLY | LAB_CREATED |
| lab-minio-s3 | OpenDAL | MinIO | INTERNAL_ONLY | DESIGN_ONLY |
| customer-owned-s3 | S3/OpenDAL | Customer S3 | EXTERNAL_BUCKET | DESIGN_ONLY |
| export-bundle | Provider | R2/local | EXPORT_BUNDLE | DESIGN_ONLY |
| internal-cache | Provider | local/R2 | NO_PUBLIC_ACCESS | DESIGN_ONLY |
| private-artifacts | Provider | R2/local | INTERNAL_STREAM | DESIGN_ONLY |

---

## Access Modes

| Mode | Description | Current |
|------|-------------|---------|
| SIGNED_URL | Generated on demand, TTL bounded, not persisted | ✅ R2 |
| INTERNAL_STREAM | Platform streams through API | Future |
| LOCAL_PATH | Lab/dev only, never user-facing | ✅ OpenDAL lab |
| DIRECT_COPY | Copy to customer bucket | Future |
| EXTERNAL_BUCKET | Customer owns bucket | Future |
| EXPORT_BUNDLE | Packaged downloadable export | Future |
| NO_PUBLIC_ACCESS | Internal only | Future |

---

## Configuration Sketch (Design-only)

```yaml
storage:
  delivery:
    default-profile: preview-r2
    profiles:
      preview-r2:
        provider: s3
        backend: r2
        access-mode: signed-url
        ttl-seconds: 900
      lab-opendal-fs:
        provider: opendal
        backend: fs
        access-mode: internal-only
        enabled: false
      lab-rustfs-s3:
        provider: opendal
        backend: s3
        endpoint: http://localhost:9000
        bucket: media-platform-lab
        access-mode: internal-only
        enabled: false
```

**Note:** Design-only. No runtime binding implemented.

---

## Profile Lifecycle

```
DESIGN_ONLY
  → LAB_CREATED
  → EVALUATION_READY
  → EVALUATING
  → EXPERIMENTAL
  → PREVIEW_VERIFIED
  → PRODUCTION_CANDIDATE
  → PRODUCTION_READY

Alternative:
  → DEFERRED
  → REJECTED
  → REMOVED
```

---

## Security / Isolation Rules

| Rule | Description |
|------|-------------|
| StorageReference internal | Never directly exposed to user API |
| AccessDescriptor on demand | No signed URL persistence, no credentials |
| Profile selection | Explicit config only, no user-controlled selection |
| Lab isolation | RustFS/OpenDAL lab disabled by default |
| Secrets | Environment/config only, redacted logs |
| Runtime safety | No startup remote I/O, lazy init |
| Promotion gate | Smoke, Docker, security review, authz model |

---

## Decision Matrix

| Profile | Provider | Backend | Access | Status | Next Task |
|---------|----------|---------|--------|--------|-----------|
| preview-r2 | S3/R2 | R2 | SIGNED_URL | PREVIEW_VERIFIED | STORAGE-R2-SIGNED-ACCESS-AUTHZ.1 |
| lab-opendal-fs | OpenDAL | fs | INTERNAL | EXPERIMENTAL | STORAGE-OPENDAL-RUSTFS-SMOKE.0 |
| lab-rustfs-s3 | OpenDAL | RustFS | INTERNAL | LAB_CREATED | STORAGE-OPENDAL-RUSTFS-SMOKE.0 |
| lab-minio-s3 | OpenDAL | MinIO | INTERNAL | DESIGN_ONLY | OPTIONAL_LATER |
| customer-s3 | S3/OpenDAL | Customer | EXTERNAL | DESIGN_ONLY | STORAGE-CUSTOMER-BUCKET-EVALUATION.0 |
| export-bundle | Provider | R2/local | BUNDLE | DESIGN_ONLY | STORAGE-EXPORT-BUNDLE-DESIGN.0 |
| internal-cache | Provider | local/R2 | NONE | DESIGN_ONLY | — |
| private-artifacts | Provider | R2/local | STREAM | DESIGN_ONLY | — |

---

## Current Decisions

| Decision | Status |
|----------|--------|
| preview-r2 default | ✅ PREVIEW_VERIFIED |
| R2/S3 provider stable | ✅ KEEP_STABLE |
| OpenDAL experimental | ✅ DISABLED_BY_DEFAULT |
| RustFS lab-only | ✅ LAB_CREATED |
| MinIO optional | DESIGN_ONLY |
| Customer storage | DESIGN_ONLY |
| AccessDescriptor stable | ✅ |
| StorageReference internal | ✅ |
| Signed URL on demand | ✅ |

---

## Follow-up Tasks

1. STORAGE-R2-SIGNED-ACCESS-AUTHZ.1
2. STORAGE-DELIVERY-PROFILE-CONTRACT.0
3. STORAGE-OPENDAL-RUSTFS-SMOKE.0
4. INGEST-TIKA-METADATA-EVALUATION.0
5. STORAGE-CUSTOMER-BUCKET-EVALUATION.0

---

## Status

- STORAGE-DELIVERY-PROFILE-DESIGN.0: COMPLETE
- Delivery profiles: DESIGN_ONLY (no runtime)
- Current R2 path: UNCHANGED
