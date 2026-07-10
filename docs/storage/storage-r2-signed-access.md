# Storage R2 Signed Access

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** STORAGE-R2-SIGNED-ACCESS.1

---

## Access Descriptor Contract

| Field | Description |
|-------|-------------|
| productId | Product ID |
| artifactId | Artifact ID |
| accessType | SIGNED_URL / UNSUPPORTED / NOT_FOUND |
| method | GET |
| url | Signed URL (ephemeral) |
| expiresAt | Expiration time |
| ttlSeconds | TTL |
| mimeType | MIME type |
| filename | Filename |
| sizeBytes | File size |
| redacted | true (always) |

---

## Implementation

| Component | Status |
|-----------|--------|
| ArtifactAccessService | ✅ CREATED |
| Access descriptor DTO | ✅ |
| S3 presigner | ⚠️ NOT YET INTEGRATED |
| Local provider | UNSUPPORTED |

---

## Security Rules

| Rule | Status |
|------|--------|
| Signed URL not persisted | ✅ |
| No bucket in response | ✅ |
| No object key in response | ✅ |
| No credentials exposed | ✅ |
| No raw local path | ✅ |
| TTL bounded | ✅ |

---

## Status

- STORAGE-R2-SIGNED-ACCESS.1: PARTIAL
- Access service: CREATED
- S3 presigner: NOT YET INTEGRATED
