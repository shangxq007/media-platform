# Staging GitOps Manifests

This directory contains the rendered Kubernetes manifests for the **staging** environment.

## How it works

1. CI renders manifests using `scripts/update-gitops-manifests.sh staging`.
2. CI creates a PR to update this directory.
3. ArgoCD/Flux syncs from this directory to `media-platform-staging` namespace.

## Manual update

```bash
REGISTRY=registry.example.com IMAGE_TAG=git-abc1234 \
  ./scripts/update-gitops-manifests.sh staging
```

## Security

- No `:latest` or `:dev` image tags allowed.
- No Secret values — only Secret references.
- No `allow-in-process-eval=true`.
- All workloads have `securityContext`.
- `sandbox-worker` has `NetworkPolicy` denying egress.
