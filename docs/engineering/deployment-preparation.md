# Production Deployment Preparation

## Overview

This document summarizes the current state of the media platform for production deployment, including feature flags, storage signing, security checks, and remaining blockers.

**Last updated:** 2026-06-04
**Current commit:** `140ba5a` (feat: stabilize prelaunch render export import pipeline)

## 1. Feature Flags Checklist

| Flag | Location | Default | Staging | Production | Notes |
|------|----------|---------|---------|------------|-------|
| `editor.effectTaxonomy.enabled` | Not implemented in code | N/A | N/A | N/A | No runtime flag exists. Effect taxonomy is always active. |

**Finding:** No feature flags are implemented in the codebase. The `editor.effectTaxonomy.enabled` flag mentioned in earlier design docs was never added to the Java code. The taxonomy system is always-on.

**Action required:** If runtime toggle is needed, add `ProjectFeatureFlag` entity and `FeatureFlagService` to `identity-access-module`. For v1, this is optional since taxonomy is additive and backward-compatible.

## 2. Storage Signing Checklist

| Item | Status | Details |
|------|--------|---------|
| `BlobStorage.presignStorageUri()` | ✅ Implemented | `S3BlobStorageProvider` and `LocalFsStorageProvider` |
| S3 credentials | ❌ Not configured in production | `SPRING_DATASOURCE_PASSWORD` and S3 creds are `REPLACE_ME` placeholders |
| Signed URL TTL | ✅ 3600s default, 86400s max | Enforced in `S3AssetDownloadUrlPort` |
| Signed URL in audit | ✅ Redacted | `ProjectExportService.recordExportAudit()` uses safe payload map |
| Signed URL in response | ✅ Present | `ProjectExportAssetDto.downloadUrl` (not storageUri) |
| Local storage root | ✅ `/data/platform` | Configured in `configmap.yaml` |

**Production prerequisites:**
1. Configure S3 credentials (access key, secret key, bucket name, endpoint)
2. Set `APP_STORAGE_LOCAL_ROOT` to appropriate volume mount
3. Ensure `BlobStorage` bean is properly configured for production environment

## 3. Egress Smoke Strict Gate

### Current Production Strict Failures

| Failure | Root Cause | Risk | Action Required |
|---------|-----------|------|-----------------|
| Smoke test disabled | `EGRESS_PROXY_SMOKE_ENABLED=false` in production configmap | Medium | Enable after staging validation |
| example.com placeholder | `allowed-domains.txt` contains `.example.com`, `.oidc.example.com`, `.s3.example.com` | Medium | Replace with real domains before production |

### Staging Configuration

| Setting | Value | Status |
|---------|-------|--------|
| `EGRESS_PROXY_SMOKE_ENABLED` | `false` | ⚠️ Enable after validation |
| `allowed-domains.txt` | Real domains should be configured | ⚠️ Verify |

### Production Promotion Gate

| Check | Location | Status |
|-------|----------|--------|
| `validate-production-readiness.sh gitops/production` | `scripts/` | ✅ PASS |
| `verify-egress-smoke-config.sh gitops/staging` | `scripts/` | ✅ PASS |
| `verify-egress-smoke-config.sh gitops/production --strict` | `scripts/` | ⚠️ Expected failure (smoke disabled + example.com) |

**Action required before production:**
1. Replace `.example.com`, `.oidc.example.com`, `.s3.example.com` with real domains
2. Enable `EGRESS_PROXY_SMOKE_ENABLED=true` after staging validation
3. Set `EGRESS_PROXY_SMOKE_URL` to real smoke test endpoint

## 4. Database Migrations

| Version | Description | Status | Conflict Check |
|---------|-------------|--------|----------------|
| V1 | Initial schema | ✅ Applied | N/A |
| V2 | Backfill audit record categories | ✅ Applied | N/A |
| V3 | Audit constraints | ✅ Applied | N/A |
| V4 | Effect taxonomy fields | ✅ Applied | N/A |
| V5 | Artifact size_bytes and checksum | ✅ Applied | ✅ No conflict |

**Migration location:** `platform-app/src/main/resources/db/migration/V5__add_artifact_size_bytes_and_checksum.sql`

**Production note:** V5 adds nullable columns (`size_bytes BIGINT NULL`, `checksum VARCHAR(128) NULL`). Existing rows will have NULL values. This is backward-compatible.

## 5. Runtime Dependencies

| Dependency | Required For | Production Config | Status |
|------------|--------------|-------------------|--------|
| ffmpeg | Render pipeline | System package or container image | ✅ Verified |
| ffprobe | Render validation | System package or container image | ✅ Verified |
| PostgreSQL | All services | `postgresql.media-platform.svc.cluster.local` | ✅ Configured |
| BlobStorage (S3/Local) | Export/Import | ConfigMap + Secret | ⚠️ Credentials needed |
| Feature flags (optional) | Runtime toggles | Not implemented | ⚠️ Optional for v1 |
| Audit service | Audit logging | `audit-compliance-module` | ✅ Included |
| Artifact catalog | Asset management | `artifact-catalog-module` | ✅ Included |

## 6. Security Checklist

| Check | Status | Details |
|-------|--------|---------|
| Export metadata_only contains no URLs | ✅ | `buildMetadataOnlyExport()` returns empty asset list |
| linked_assets export contains no storageUri | ✅ | `ProjectExportAssetDto.storageRef = null` |
| Signed URL TTL finite | ✅ | Max 86400s enforced |
| Signed URL not in audit | ✅ | Audit payload uses safe map without URLs |
| Import SSRF protection | ✅ | `SafeDownloadUrlValidator` + `ImportAssetDownloader` |
| Checksum/size validation | ✅ | `ProjectImportService` validates before storage |
| Rollback cleanup | ✅ | `ImportCleanupTracker` with blob + artifact cleanup |
| Wrong tenant denied | ✅ | `assertTenantAccess()` check in service |
| StorageUri not in API response | ✅ | DTO excludes storageUri/storageRef |
| Signed URL not in audit | ✅ | Audit uses `exportId`, `mode`, `tenantId`, `assetCount` only |

## 7. Remaining Blockers Before Production Promotion

### P0 (Must fix)

| Blocker | Description | Action |
|---------|-------------|--------|
| Production secrets | `SPRING_DATASOURCE_PASSWORD`, `APP_JWT_SECRET`, etc. are `REPLACE_ME` | Replace with real secrets |
| S3 credentials | Not configured for production | Configure BlobStorage credentials |
| Allowed domains | `example.com` placeholder in production | Replace with real domains |

### P1 (Should fix)

| Blocker | Description | Action |
|---------|-------------|--------|
| Smoke test disabled | `EGRESS_PROXY_SMOKE_ENABLED=false` | Enable after staging |
| Smoke URL empty | `EGRESS_PROXY_SMOKE_URL=""` | Set real URL |

### P2 (Can defer)

| Blocker | Description | Action |
|---------|-------------|--------|
| Feature flags not implemented | No runtime toggle for effect taxonomy | Add if needed |
| Zip packaging | Not implemented | Future task |

## 8. Deployment Checklist

- [ ] Replace production secrets in `gitops/production/secret.yaml`
- [ ] Configure S3/Local storage credentials
- [ ] Replace `example.com` domains in `gitops/production/configmap-egress-proxy.yaml`
- [ ] Enable smoke test in staging first
- [ ] Validate staging deployment
- [ ] Run `validate-production-readiness.sh gitops/production`
- [ ] Run `verify-egress-smoke-config.sh gitops/staging`
- [ ] Verify no new failures in CI
- [ ] Create production promotion PR
- [ ] Do NOT enable `EGRESS_PROXY_SMOKE_ENABLED` in production until staging is validated

## 9. Pre-Deployment Verification Commands

```bash
# Backend
cd platform
./gradlew compileJava compileTestJava
./gradlew :shared-kernel:test :identity-access-module:test :artifact-catalog-module:test :storage-module:test :audit-compliance-module:test :render-module:test
./gradlew :platform-app:test --tests '*production*'

# Frontend
cd platform/frontend
npm run typecheck
npx vitest run

# Readiness
scripts/validate-production-readiness.sh gitops/production
scripts/verify-egress-smoke-config.sh gitops/staging
scripts/verify-egress-smoke-config.sh gitops/production --strict || true
```

## 10. Next Steps

1. **Immediate:** Replace production secrets and configure storage credentials
2. **Staging first:** Deploy to staging, enable smoke test, validate
3. **Production promotion:** After staging validation, promote to production
4. **Future:** Implement zip packaging (P4-EXPORT-4), feature flags if needed
