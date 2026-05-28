# Production GitOps Manifests

This directory contains the rendered Kubernetes manifests for the **production** environment.

## How it works

1. CI renders manifests using `scripts/update-gitops-manifests.sh production` (only on release/manual trigger).
2. CI creates a PR to update this directory.
3. PR requires manual review and merge.
4. ArgoCD/Flux syncs from this directory to `media-platform` namespace.

## Manual update

```bash
REGISTRY=registry.example.com IMAGE_TAG=v1.2.3 \
  ./scripts/update-gitops-manifests.sh production
```

## Security

- No `:latest` or `:dev` image tags allowed.
- No Secret values — only Secret references.
- No `allow-in-process-eval=true`.
- No `dev-auth-endpoint=true`.
- All workloads have `securityContext`.
- `sandbox-worker` has `NetworkPolicy` denying egress.
- No automated sync — requires manual approval.
