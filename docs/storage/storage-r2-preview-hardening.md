# Storage R2 Preview Hardening

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-R2-PREVIEW-HARDENING.0

---

## R2 Configuration Contract

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `STORAGE_S3_ENABLED` | Enable S3/R2 storage | `true` |
| `STORAGE_S3_DEFAULT_BUCKET` | Default bucket name | `render-cache` |
| `STORAGE_S3_COMPATIBILITY` | R2 compatibility mode | `r2` |
| `STORAGE_S3_ENDPOINT` | R2 endpoint URL | `https://xxx.r2.cloudflarestorage.com` |
| `STORAGE_S3_ACCESS_KEY_ID` | R2 access key | `[PLACEHOLDER]` |
| `STORAGE_S3_SECRET_ACCESS_KEY` | R2 secret key | `[PLACEHOLDER]` |

### Profile Activation

```bash
# Local development (no R2)
java -jar platform-app.jar

# Preview with R2
java -jar platform-app.jar --spring.profiles.active=prod,r2
```

---

## R2 Provider Behavior

| Behavior | Status |
|----------|--------|
| Local provider default | ✅ |
| R2 provider when configured | ✅ |
| R2 compatibility mode | ✅ (disables chunked encoding) |
| Write/upload | ✅ |
| Read/materialize | ✅ |
| HEAD/stat | ✅ |
| Physical check | ✅ |

---

## Object Key Namespace

**Format:** `preview/{tenantId}/{projectId}/{productId}/{artifactType}/{filename}`

**Rules:**
- No raw local paths
- No secrets
- Path traversal prevented
- Filename normalized
- Internal storage metadata only

---

## Signed URL Boundary

| Rule | Status |
|------|--------|
| Access-layer only | ✅ |
| Bounded TTL | ✅ |
| Not canonical reference | ✅ |
| Not persisted as storageReferenceId | ✅ |

---

## Physical Check Integration

| Check | Status |
|-------|--------|
| R2 HEAD/stat | ✅ |
| STORAGE_OBJECT_MISSING | ✅ |
| STORAGE_OBJECT_CHECK_FAILED | ✅ |
| Referenced only | ✅ |
| No bucket scan | ✅ |

---

## Security

| Rule | Status |
|------|--------|
| No credentials in docs | ✅ |
| No signed URLs in reports | ✅ |
| No secrets in frontend | ✅ |
| No raw local paths | ✅ |

---

## Status

- STORAGE-R2-PREVIEW-HARDENING.0: COMPLETE
- R2 config contract: DOCUMENTED
- R2 provider: EXISTS
- Physical check: SUPPORTED
