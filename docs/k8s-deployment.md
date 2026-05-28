# K8s Deployment Guide

## Overview

K8s manifests in `k8s/` use `:dev` as a local development placeholder.
**Production deployments must use rendered manifests** with an immutable image tag.

## Prerequisites

- Docker registry access (e.g., GHCR, Docker Hub, ECR)
- `kubectl` configured for target cluster
- Images built and pushed to registry

## Build & Push Images

```bash
# Compute immutable tag
export IMAGE_TAG="git-$(git rev-parse --short=12 HEAD)"
export REGISTRY="ghcr.io/yourorg"

# Build API image
docker build -t "$REGISTRY/platform-api:$IMAGE_TAG" .

# Build render-worker image
docker build -t "$REGISTRY/platform-render-worker:$IMAGE_TAG" remote-render-worker/

# Push
docker push "$REGISTRY/platform-api:$IMAGE_TAG"
docker push "$REGISTRY/platform-render-worker:$IMAGE_TAG"
```

## Render K8s Manifests

```bash
REGISTRY="$REGISTRY" IMAGE_TAG="$IMAGE_TAG" ./scripts/render-k8s-manifests.sh
```

Output: `build/k8s/*.yaml` with explicit image references.

**Validation rules:**
- `REGISTRY` is required
- `IMAGE_TAG` is required
- `IMAGE_TAG` must NOT be `latest`
- `IMAGE_TAG` must NOT be `dev`

## Deploy

```bash
# Apply rendered manifests
kubectl apply -f build/k8s/

# Or apply specific resources
kubectl apply -f build/k8s/namespace.yaml
kubectl apply -f build/k8s/configmap.yaml
kubectl apply -f build/k8s/secret.yaml
kubectl apply -f build/k8s/pvc.yaml
kubectl apply -f build/k8s/deployment-api.yaml
kubectl apply -f build/k8s/deployment-render-worker.yaml
kubectl apply -f build/k8s/service-api.yaml
kubectl apply -f build/k8s/service-render-worker.yaml
kubectl apply -f build/k8s/ingress.yaml
kubectl apply -f build/k8s/hpa.yaml
```

## Rollback

```bash
# Rollback to previous revision
kubectl rollout undo deployment/platform-api
kubectl rollout undo deployment/platform-render-worker

# Or set specific image
kubectl set image deployment/platform-api \
  api=$REGISTRY/platform-api:<OLD_TAG>
kubectl set image deployment/platform-render-worker \
  render-worker=$REGISTRY/platform-render-worker:<OLD_TAG>
```

## CI/CD

The GitHub Actions workflow (`.github/workflows/ci.yml`) automatically:
1. Runs tests
2. Builds images on main branch push
3. Pushes to GHCR with tag `git-<short-sha>`
4. Renders K8s manifests
5. Uploads rendered manifests as artifact

## Image Tag Rules

| Tag | Use | Production? |
|-----|-----|-------------|
| `git-abc1234def` | CI-generated immutable tag | ✅ Yes |
| `v1.2.3` | Semver release tag | ✅ Yes |
| `20250526-143000-abc1234` | Date+SHA | ✅ Yes |
| `dev` | Local development placeholder | ❌ No |
| `latest` | Never use | ❌ No |

## Security Context

All containers run with:
- `runAsNonRoot: true`
- `readOnlyRootFilesystem: true`
- `allowPrivilegeEscalation: false`
- `capabilities.drop: ALL`
- `seccompProfile: RuntimeDefault`

API runs as UID 10001, render-worker as UID 10002.
