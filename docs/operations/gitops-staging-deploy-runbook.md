---
status: runbook
last_verified: 2026-06-18
scope: staging
truth_level: implemented
owner: platform
---

# GitOps Staging Deployment Runbook

## Overview

This runbook documents the deployment procedures for staging environments using GitOps principles with ArgoCD.

## Prerequisites

- Kubernetes cluster access
- ArgoCD installed and configured
- kubectl configured
- Docker registry access

## Deployment Process

### 1. Update Application Image

```bash
# Build and push new image
docker build -t registry.example.com/media-platform:$TAG .
docker push registry.example.com/media-platform:$TAG
```

### 2. Update Kubernetes Manifests

```bash
# Update image tag in manifests
cd k8s/staging
kustomize edit set image media-platform=registry.example.com/media-platform:$TAG
```

### 3. Commit and Push

```bash
git add .
git commit -m "chore(deploy): update staging to $TAG"
git push origin main
```

### 4. ArgoCD Sync

ArgoCD will automatically detect changes and sync, or manually trigger:

```bash
argocd app sync media-platform-staging
```

## Validation

### Check Deployment Status

```bash
kubectl get pods -n media-platform-staging
kubectl get services -n media-platform-staging
```

### Verify Health

```bash
curl -s https://staging.example.com/actuator/health
```

### Check Logs

```bash
kubectl logs -f deployment/media-platform -n media-platform-staging
```

## Rollback

### Automatic Rollback

ArgoCD will automatically rollback if health checks fail.

### Manual Rollback

```bash
argocd app rollback media-platform-staging
```

## Troubleshooting

### Pod Not Starting

```bash
kubectl describe pod <pod-name> -n media-platform-staging
kubectl logs <pod-name> -n media-platform-staging
```

### Health Check Failures

```bash
kubectl exec -it <pod-name> -n media-platform-staging -- curl localhost:8080/actuator/health
```

### Database Connection Issues

```bash
kubectl exec -it <pod-name> -n media-platform-staging -- env | grep SPRING_DATASOURCE
```
