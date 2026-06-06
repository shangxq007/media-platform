# Required Staging Inputs

This document lists all environment-specific inputs required to deploy the media platform to staging.

## How to Use

1. Collect values for all **Required** fields from your infrastructure/DevOps team
2. Fill in the **Current Value** column
3. Once all required fields are filled, proceed to P4-STAGING-CONFIG-2 to apply them

---

## Required Staging Inputs

| # | Variable | Required | Example | Used By | Validation |
|---|----------|----------|---------|---------|------------|
| 1 | `APP_PUBLIC_DOMAIN` | ✅ Required | `staging.media-platform.example.com` | ingress.yaml, OAuth2 redirect URIs | `nslookup <domain>` resolves |
| 2 | `OIDC_ISSUER_DOMAIN` | ✅ Required | `auth.staging.example.com` | OAuth2/OIDC configmap, security module | HTTPS accessible, JWKS endpoint reachable |
| 3 | `STORAGE_PUBLIC_DOMAIN` | ⚠️ Required for linked_assets | `s3.staging.example.com` or `minio.staging.svc` | linked_assets export, signed URL generation | `curl -I https://<domain>` returns 200 or 403 |
| 4 | `EGRESS_SMOKE_URL` | ⚠️ Required for smoke gate | `https://httpbin.staging.example.com/get` | verify-egress-smoke-config.sh | Host must be in allowed-domains.txt |
| 5 | `STORAGE_PROVIDER` | ✅ Required | `s3` / `minio` / `local` | storage module auto-configuration | Must match available BlobStorage impl |
| 6 | `S3_ENDPOINT` | ⚠️ Required if provider=s3/minio | `https://s3.amazonaws.com` | S3BlobStorageProvider | HTTPS accessible |
| 7 | `S3_REGION` | ⚠️ Required if provider=s3 | `us-east-1` | S3BlobStorageProvider | Valid AWS region or custom |
| 8 | `S3_BUCKET` | ⚠️ Required if provider=s3/minio | `media-platform-staging` | S3BlobStorageProvider | Bucket exists, app has read/write |
| 9 | `S3_ACCESS_KEY_SECRET_NAME` | ⚠️ Required if provider=s3/minio | `s3-credentials` | Kubernetes Secret reference | Secret exists in namespace |
| 10 | `S3_SECRET_KEY_SECRET_NAME` | ⚠️ Required if provider=s3/minio | (same as above) | Kubernetes Secret reference | Secret exists in namespace |
| 11 | `DATABASE_SECRET_NAME` | ✅ Required | `app-secrets` | Kubernetes Secret reference | Contains SPRING_DATASOURCE_PASSWORD |
| 12 | `JWT_SECRET_NAME` | ✅ Required | `app-secrets` | Kubernetes Secret reference | Contains APP_JWT_SECRET |
| 13 | `OTHER_REPLACE_ME_SECRETS` | ✅ Required | `app-secrets` | Kubernetes Secret | All REPLACE_ME values replaced |

---

## Current Values (Fill In)

| # | Variable | Current Value | Source |
|---|----------|---------------|--------|
| 1 | `APP_PUBLIC_DOMAIN` | **MISSING** | _Provide staging app domain_ |
| 2 | `OIDC_ISSUER_DOMAIN` | **MISSING** | _Provide OIDC issuer domain_ |
| 3 | `STORAGE_PUBLIC_DOMAIN` | **MISSING** | _Provide storage domain_ |
| 4 | `EGRESS_SMOKE_URL` | **MISSING** | _Provide smoke test URL_ |
| 5 | `STORAGE_PROVIDER` | `local` (default) | _Confirm or change_ |
| 6 | `S3_ENDPOINT` | N/A (local) | _Required if not local_ |
| 7 | `S3_REGION` | N/A (local) | _Required if s3_ |
| 8 | `S3_BUCKET` | N/A (local) | _Required if not local_ |
| 9 | `S3_ACCESS_KEY_SECRET_NAME` | N/A (local) | _Required if not local_ |
| 10 | `S3_SECRET_KEY_SECRET_NAME` | N/A (local) | _Required if not local_ |
| 11 | `DATABASE_SECRET_NAME` | `app-secrets` (default) | _Confirm_ |
| 12 | `JWT_SECRET_NAME` | `app-secrets` (default) | _Confirm_ |
| 13 | `OTHER_REPLACE_ME_SECRETS` | `app-secrets` (default) | _Confirm all values replaced_ |

---

## Linked Assets Staging Strategy

### Option A: Local Storage (Current Default)

If `STORAGE_PROVIDER=local`:
- `APP_STORAGE_LOCAL_ROOT=/data/platform`
- `linked_assets` export will have empty `signedUrls`
- Only `metadata_only` export is fully testable
- No external signed URL generation needed

**Limitation:** Cannot validate full linked_assets round-trip in staging.

### Option B: S3/MinIO (Recommended for Full Validation)

If `STORAGE_PROVIDER=s3` or `minio`:
- Requires fields 3, 6-10 above
- `BlobStorage.presignStorageUri()` generates signed URLs
- Signed URLs must be externally reachable from test environment
- Validates full export/import pipeline

**Prerequisites:**
1. S3/MinIO bucket created and accessible from cluster
2. Kubernetes Secret with credentials created in `media-platform-staging` namespace
3. `STORAGE_PUBLIC_DOMAIN` must match the endpoint used by tests

### Decision Matrix

| Scenario | Storage Provider | linked_assets Testable | Smoke Required |
|----------|------------------|----------------------|----------------|
| Quick staging validation | local | No (metadata only) | No |
| Full e2e with file download | s3/minio | Yes | Yes |
| Production-like staging | s3/minio | Yes | Yes |

---

## Smoke Rollout Prerequisites

### Before Enabling Smoke

1. ✅ `APP_PUBLIC_DOMAIN` resolves and serves the app
2. ✅ `OIDC_ISSUER_DOMAIN` is accessible and JWKS endpoint responds
3. ✅ `EGRESS_SMOKE_URL` is a reachable HTTPS endpoint
4. ✅ Smoke URL host is listed in `allowed-domains.txt`
5. ✅ `EGRESS_PROXY_SMOKE_ENABLED=true` in configmap
6. ✅ `EGRESS_PROXY_SMOKE_URL` set in configmap

### Smoke Validation Command

```bash
cd platform
scripts/verify-egress-smoke-config.sh gitops/staging
```

Expected result after all prerequisites met:
```
PASS:     21
FAIL:     0
WARN:     0
```

---

## Per-Field Configuration Mapping

### 1. APP_PUBLIC_DOMAIN

**Used in:** `gitops/staging/ingress.yaml`
```yaml
spec:
  rules:
    - host: <APP_PUBLIC_DOMAIN>
      http:
        paths:
          - path: /
            backend:
              service:
                name: api
```

**Validation:**
```bash
nslookup <APP_PUBLIC_DOMAIN>
curl -I https://<APP_PUBLIC_DOMAIN>
```

### 2. OIDC_ISSUER_DOMAIN

**Used in:** `gitops/staging/configmap.yaml`
```yaml
data:
  APP_SECURITY_OAUTH2_ISSUER_URI: "https://<OIDC_ISSUER_DOMAIN>"
```

**Validation:**
```bash
curl -I https://<OIDC_ISSUER_DOMAIN>/.well-known/openid-configuration
```

### 3. STORAGE_PUBLIC_DOMAIN

**Used in:** `gitops/staging/configmap-egress-proxy.yaml` (allowed-domains.txt)
```yaml
data:
  allowed-domains.txt: |
    .<STORAGE_PUBLIC_DOMAIN>
```

**Validation:**
```bash
curl -I https://<STORAGE_PUBLIC_DOMAIN>
```

### 4. EGRESS_SMOKE_URL

**Used in:** `gitops/staging/configmap.yaml`
```yaml
data:
  EGRESS_PROXY_SMOKE_ENABLED: "true"
  EGRESS_PROXY_SMOKE_URL: "<EGRESS_SMOKE_URL>"
```

**Validation:**
```bash
curl -I <EGRESS_SMOKE_URL>
# Verify host is in allowed-domains.txt
grep "$(echo <EGRESS_SMOKE_URL> | sed 's|https\?://||' | cut -d/ -f1)" gitops/staging/configmap-egress-proxy.yaml
```

### 5-10. S3/MinIO Fields

**Used in:** `gitops/staging/configmap.yaml` and referenced secrets

```yaml
data:
  STORAGE_PROVIDER: "s3"
  S3_ENDPOINT: "<S3_ENDPOINT>"
  S3_REGION: "<S3_REGION>"
  S3_BUCKET: "<S3_BUCKET>"
```

**Secrets referenced:**
```bash
kubectl get secret <S3_ACCESS_KEY_SECRET_NAME> -n media-platform-staging
```

### 11-13. Secrets

**Used in:** `gitops/staging/secret.yaml`
```yaml
stringData:
  SPRING_DATASOURCE_PASSWORD: "<REPLACE>"
  APP_JWT_SECRET: "<REPLACE>"
```

**Validation:**
```bash
kubectl get secret app-secrets -n media-platform-staging -o yaml
# Verify no REPLACE_ME values remain
```

---

## Checklist for DevOps/Infrastructure Team

Please provide the following:

### Infrastructure

- [ ] **Staging app domain** (e.g., `staging.media-platform.example.com`)
- [ ] **Staging OIDC domain** (e.g., `auth.staging.example.com`)
- [ ] **Staging storage domain** (e.g., `s3.staging.example.com` or MinIO endpoint)

### Storage Decision

- [ ] **Storage provider for staging:** `s3` / `minio` / `local`
- [ ] If s3/minio:
  - [ ] Endpoint URL
  - [ ] Region
  - [ ] Bucket name
  - [ ] Access key ID (do NOT email — use secure channel)
  - [ ] Secret access key (do NOT email — use secure channel)
  - [ ] Secret name in Kubernetes

### Smoke Test

- [ ] **Smoke test URL** (should return 200 OK via HTTPS)
- [ ] Confirm smoke URL host will be added to allowed-domains.txt

### Secrets

- [ ] **Database password** (staging PostgreSQL)
- [ ] **JWT secret** (min 256 bits)
- [ ] **Remote worker API key**
- [ ] Confirm all REPLACE_ME values in secret.yaml are replaced

### Verification Commands (After Applying)

```bash
# Compile
cd platform && ./gradlew compileJava compileTestJava

# Backend tests
./gradlew :shared-kernel:test :identity-access-module:test :artifact-catalog-module:test :storage-module:test :render-module:test

# Frontend (must run from frontend/ directory)
cd platform/frontend && npm run typecheck && npx vitest run

# Readiness
cd platform && bash scripts/validate-production-readiness.sh gitops/production

# Staging egress (after applying staging config)
bash scripts/verify-egress-smoke-config.sh gitops/staging
```

---

## Missing Values Summary

| Priority | Missing Fields |
|----------|---------------|
| **Critical** | `APP_PUBLIC_DOMAIN`, `OIDC_ISSUER_DOMAIN` |
| **Required for smoke** | `EGRESS_SMOKE_URL` |
| **Required for linked_assets** | `STORAGE_PUBLIC_DOMAIN`, `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_SECRET_NAME`, `S3_SECRET_KEY_SECRET_NAME` |
| **Required for secrets** | All REPLACE_ME values in `secret.yaml` |

---

## Next Steps

Once all required inputs are collected:

1. Update `gitops/staging/configmap.yaml` with real values
2. Update `gitops/staging/configmap-egress-proxy.yaml` with real domains
3. Update `gitops/staging/secret.yaml` with real secrets (via secure channel)
4. Update `gitops/staging/ingress.yaml` with real domain
5. Run `scripts/verify-egress-smoke-config.sh gitops/staging`
6. Deploy to staging namespace
7. Run smoke test manually
8. Proceed to P4-STAGING-CONFIG-2

---

## Security Notes

- **Never commit real secrets to git**
- Use Sealed Secrets, External Secrets Operator, or manual secret creation
- Signed URL TTL: default 3600s, max 86400s
- All external domains must be in allowed-domains.txt
- Smoke test should NOT affect pod readiness (INCLUDE_IN_READINESS=false)
