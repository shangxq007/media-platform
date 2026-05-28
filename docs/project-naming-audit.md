# Project Naming Audit

## 1. Current State

The project historically used `example` as a placeholder name during initial development.
Current system deployment naming already uses `media-platform` extensively:
- K8s namespace: `media-platform` / `media-platform-staging` / `media-platform-production`
- K8s app labels: `app: media-platform`, `app: sandbox-worker`
- Docker images: `platform-api`, `platform-render-worker`, `platform-sandbox-worker`
- Service names: `api`, `render-worker`, `sandbox-worker`, `egress-proxy`
- README title: "Media Platform"

However, Java package remains `com.example.platform` across all 31 Gradle modules.
The `example.com` domain appears in multiple configuration placeholders.

### Scan Statistics

| Category | Count | Risk Level |
|----------|-------|------------|
| `com.example` Java package references | ~4638 | High — defer |
| `example.com` config/doc placeholders | ~124 | Keep — placeholder |
| `ghcr.io/example/` in generated GitOps output | 8 | High — defer (registry placeholder) |
| `com.example.platform.jooq.generated` in scripts | 2 | High — defer |
| Frontend display name / package.json | 0 | N/A |
| README project title | 0 (already "Media Platform") | N/A |
| Frontend test descriptions | 0 | N/A |
| CI references | 1 | Keep — checklist placeholder |
| Docs "for example" usage | ~15 | Keep — natural language |
| `.env.example` file references | ~5 | Keep — standard file name |

**Low-risk items to replace: 0**

All `example` references in the codebase are either:
1. Java package names (`com.example.platform`) — high risk, defer
2. Domain placeholders (`example.com`) — keep as documentation/config placeholders
3. Natural language ("for example", "documentation example") — keep
4. Standard file names (`.env.example`) — keep

## 2. Naming Standard

| Dimension | Standard | Current Status |
|-----------|----------|----------------|
| Product display name | Media Platform | ✅ Already in README |
| System short name | media-platform | ✅ Already in K8s/Docker |
| K8s namespace | media-platform / media-platform-staging / media-platform-production | ✅ Already configured |
| Docker images | platform-api / platform-render-worker / platform-sandbox-worker / egress-proxy | ✅ Already configured |
| K8s app labels | app: media-platform, app: sandbox-worker, app: egress-proxy | ✅ Already configured |
| Service names | api, render-worker, sandbox-worker, egress-proxy | ✅ Already configured |
| Java package | com.example.platform (current) → TBD (future) | ⏳ Deferred |
| Placeholder domains | example.com (documentation/config) | ✅ Keep as placeholder |
| Git registry | ghcr.io/example/ (rendered output placeholder) | ⏳ Deferred — requires real registry |

## 3. Processed Low-Risk Items

**None.** All `example` references are either high-risk or legitimate placeholders.

## 4. Retained Placeholders

The following `example.com` references are intentional configuration/documentation placeholders:

### K8s Config (configmap-egress-proxy.yaml)
- `.oidc.example.com` — OIDC issuer domain placeholder
- `.s3.example.com` — S3/MinIO endpoint placeholder
- `.alerts.example.com` — Webhook alert endpoint placeholder
- `.litellm.example.com` — LiteLLM/AI provider placeholder

### K8s Config (secret.yaml)
- `https://auth.example.com` — OAuth2 issuer URI placeholder

### K8s Config (ingress.yaml)
- `api.media-platform.example.com` — Ingress hostname placeholder

### K8s Comments (deployment-*.yaml)
- `registry.example.com/platform-api:2025.05.25-abc1234` — Image tag example

### Docs
- `application/vnd.example.v2+json` — API versioning example (Stripe-style)
- Various "for example" natural language usage

### CI
- `allowed-domains.txt has real production domains (no example.com)` — Checklist item

### Scripts
- `.env.example` — Standard environment template file name

**These should NOT be replaced until real production domains are configured.**

## 5. High-Risk Deferred Items

### 5.1 Java Package Migration (`com.example.platform`)

- **Scope**: 4638 references across 31 Gradle modules
- **Risk**: Breaks all imports, Spring component scan, reflection, serialization
- **Files affected**: Every `.java`, `.kt`, `.kts` file; `build.gradle.kts`; `settings.gradle.kts`; Flyway callbacks
- **Strategy**: Requires dedicated multi-step migration:
  1. Design target package name (e.g., `io.bluepulse.platform` or `com.bluepulse.media`)
  2. Use IntelliJ refactoring for safe rename
  3. Update Spring scan paths
  4. Update Flyway callbacks
  5. Update jOOQ generated code config
  6. Full regression test
- **Recommended task**: P4-0b-2 (design) + P4-0b-3 (execute)

### 5.2 GitOps Registry Path (`ghcr.io/example/`)

- **Scope**: 8 files in gitops/staging and gitops/production (generated output)
- **Risk**: Requires real container registry
- **Strategy**: Update `REGISTRY` env var in CI/render scripts to real registry
- **Recommended task**: P4-0b-4 (after registry is provisioned)

### 5.3 jOOQ Generated Package

- **Scope**: `scripts/generate-jooq.sh` references `com.example.platform.jooq.generated`
- **Risk**: Tied to Java package migration
- **Strategy**: Update alongside Java package migration
- **Recommended task**: P4-0b-3

### 5.4 K8s Ingress Hostname

- **Scope**: `api.media-platform.example.com` in ingress.yaml
- **Risk**: Requires real domain and DNS
- **Strategy**: Replace with actual domain after DNS is configured
- **Recommended task**: P4-0b-5

### 5.5 OAuth2 Issuer URI

- **Scope**: `https://auth.example.com` in secret.yaml
- **Risk**: Requires real OIDC provider
- **Strategy**: Replace with actual OIDC issuer after provider is configured
- **Recommended task**: P4-0b-5

### 5.6 Allowed-Domains Placeholders

- **Scope**: `.oidc.example.com`, `.s3.example.com`, `.alerts.example.com`, `.litellm.example.com` in configmap-egress-proxy.yaml
- **Risk**: Requires real production domains and staging smoke verification
- **Strategy**: Replace after completing P3-10e staging smoke rollout
- **Recommended task**: P3-10e completion

## 6. Migration Recommendations

| Task | Description | Dependencies | Risk |
|------|-------------|--------------|------|
| P4-0b-2 | Java package migration design | None | Low (design only) |
| P4-0b-3 | Java package migration execution | P4-0b-2, local core render tests | High |
| P4-0b-4 | K8s/Docker registry naming | Real container registry provisioned | Medium |
| P4-0b-5 | Production domain placeholder replacement | Real domains, staging smoke verified | Medium |

### Suggested Order

1. **P4-0b-2** (design): Decide target package name. No code changes.
2. **P4-0b-3** (execute): Java package rename. Requires:
   - Local core render functionality tests passing
   - Full regression after rename
   - Spring component scan verification
3. **P4-0b-4** (registry): Update GitOps image paths. Requires:
   - Real container registry (e.g., `ghcr.io/bluepulse-media/`)
   - CI workflow update
4. **P4-0b-5** (domains): Replace placeholder domains. Requires:
   - Real OIDC issuer, S3 endpoint, webhook domain
   - Staging smoke rollout complete
   - Production strict gate passing
