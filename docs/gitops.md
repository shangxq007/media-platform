# GitOps Deployment Guide

## Overview

This project uses a GitOps approach for deploying to staging and production environments.
Kubernetes manifests are rendered from base + overlay templates and committed to the `gitops/` directory.
ArgoCD/Flux syncs from this directory to the target namespace.

## Directory Structure

```
gitops/
├── staging/                    # Staging environment manifests
│   ├── README.md
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml             # References only, no values
│   ├── deployment-api.yaml
│   ├── deployment-render-worker.yaml
│   ├── deployment-sandbox-worker.yaml
│   ├── service-*.yaml
│   ├── ingress.yaml
│   └── networkpolicy-sandbox-worker.yaml
├── production/                 # Production environment manifests
│   ├── README.md
│   └── ... (same structure)
└── argocd/                     # ArgoCD Application examples
    ├── application-staging.yaml
    └── application-production.yaml
```

## How it works

1. **CI builds images** with immutable tag `git-<shortSha>`.
2. **CI renders manifests** using `scripts/update-gitops-manifests.sh`.
3. **CI creates a PR** to update `gitops/` directory.
4. **ArgoCD/Flux syncs** from `gitops/` to the target namespace.

## Staging

- **Trigger**: Every push to `main` branch (automatic) or `workflow_dispatch`.
- **PR**: Created automatically with tag `git-<shortSha>`.
- **Sync**: ArgoCD auto-sync on merge (`syncPolicy.automated`).
- **Namespace**: `media-platform-staging`.
- **Purpose**: Pre-production validation.

### Automatic flow (main branch push)

```
push to main → build images → render staging manifests → create PR → merge → ArgoCD sync
```

### Manual flow (workflow_dispatch)

```bash
# GitHub Actions → Run workflow → environment=staging, imageTag=git-abc123
```

## Production

- **Trigger**: `workflow_dispatch` only (manual).
- **PR**: Created with validation checklist.
- **Sync**: Manual approval required (no automated sync).
- **Namespace**: `media-platform-production`.
- **Purpose**: Live production.

### Promotion flow

```bash
# GitHub Actions → Run workflow → environment=production, imageTag=v1.2.3
# → renders production manifests
# → runs production readiness validation (FAIL blocks PR creation)
# → creates PR with checklist
# → reviewer approval required
# → merge → ArgoCD manual sync
```

**Production readiness validation** runs automatically before the PR is created.
If the validation finds any FAIL issues, the PR is NOT created and the workflow fails.

**Never uses `kubectl apply` in CI for production.**

## workflow_dispatch Inputs

| Input | Required | Description |
|-------|----------|-------------|
| `environment` | Yes | `staging` or `production` |
| `imageTag` | Yes (production) | Immutable image tag (e.g. `git-abc123def456`) |
| `registry` | No | Container registry (default: `ghcr.io/<owner>`) |

## CI Loop Prevention

GitOps PRs updating `gitops/**` do NOT trigger a new CI build.
The CI workflow has `paths-ignore: ["gitops/**", "docs/gitops.md"]` on push triggers.

## Permissions Required

The GitHub Actions workflow needs:

```yaml
permissions:
  contents: write      # Update gitops/ directory
  pull-requests: write # Create PRs
  packages: write      # Push container images
```

In **Settings → Actions → General**:
- ✅ Read and write permissions
- ✅ Allow GitHub Actions to create and approve pull requests (if needed)

## ArgoCD Setup

1. Install ArgoCD in your cluster.
2. Apply the ArgoCD Application manifests:
   ```bash
   kubectl apply -f gitops/argocd/application-staging.yaml
   kubectl apply -f gitops/argocd/application-production.yaml
   ```
3. Update `repoURL` in the Application manifests to your Git repo URL.
4. For staging: enable `syncPolicy.automated`.
5. For production: keep manual sync.

## Manual Update

```bash
# Staging
REGISTRY=ghcr.io/yourorg IMAGE_TAG=git-abc1234 \
  ./scripts/update-gitops-manifests.sh staging

# Production
REGISTRY=ghcr.io/yourorg IMAGE_TAG=v1.2.3 \
  ./scripts/update-gitops-manifests.sh production
```

## Rollback

1. **Revert the GitOps PR** — ArgoCD will auto-sync (staging) or manual sync (production).
2. **Dispatch workflow with previous IMAGE_TAG** — Creates new PR with old manifests.
3. **kubectl rollout undo** — Direct rollback (emergency only).

## Security

- No `:latest` or `:dev` image tags.
- No Secret values in manifests — only references.
- No `allow-in-process-eval=true` in production.
- No `dev-auth-endpoint=true` in production.
- All workloads have `securityContext`.
- `sandbox-worker` has `NetworkPolicy` denying egress.
- `platform-api` has egress `NetworkPolicy` allowing only DNS, sandbox-worker, DB, storage, egress-proxy.
- `render-worker` has egress `NetworkPolicy` allowing only DNS, platform-api, DB, storage, egress-proxy.
- `egress-proxy` (Squid) provides unified external egress for OIDC/S3/webhook/providers.
- `egress-proxy` ingress only allows platform-api and render-worker (NOT sandbox-worker).
- `egress-proxy` Squid config uses `allowed_domains` ACL — only listed domains are reachable.
- `egress-proxy` Squid config has `http_access deny all` — no open proxy.
- `platform-api` and `render-worker` inject HTTP_PROXY/HTTPS_PROXY/NO_PROXY env vars.
- `sandbox-worker` has NO proxy env vars (fully isolated).
- No `0.0.0.0/0` or `169.254.169.254` in application egress policies.
- `0.0.0.0/0` allowed only in egress-proxy policy (controlled egress point).
- Production requires manual approval for sync.
- Production promotion requires staging smoke rollout per [Egress Smoke Rollout Runbook](egress-smoke-rollout.md).
- Production promotion CI runs `verify-egress-smoke-config.sh --strict` — blocks PR if smoke disabled or domains are placeholder.
- Staging CI runs `verify-egress-smoke-config.sh` (normal mode) — blocks PR on FAIL, allows WARN.
- PRs include validation checklist.
