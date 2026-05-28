#!/usr/bin/env bash
# update-gitops-manifests.sh — Render K8s manifests and sync to GitOps directory.
#
# Usage:
#   REGISTRY=ghcr.io/yourorg IMAGE_TAG=git-abc1234 ./scripts/update-gitops-manifests.sh staging
#   REGISTRY=ghcr.io/yourorg IMAGE_TAG=v1.2.3 ./scripts/update-gitops-manifests.sh production
#
# This script:
#   1. Renders K8s manifests for the given environment
#   2. Syncs the rendered manifests to the gitops/ directory
#   3. Preserves README.md in the gitops directory
#   4. Validates no :dev/:latest tags
#   5. Validates security requirements for production
#
# Output:
#   gitops/<environment>/*.yaml

set -euo pipefail

# ── Validate inputs ──────────────────────────────────────────────────────
ENVIRONMENT="${1:?Usage: $0 <staging|production>}"
REGISTRY="${REGISTRY:?REGISTRY is required, e.g. REGISTRY=ghcr.io/yourorg}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required, e.g. IMAGE_TAG=git-abc1234}"

# Reject unsafe tags
if [ "$IMAGE_TAG" = "latest" ] || [ "$IMAGE_TAG" = "dev" ]; then
  echo "ERROR: IMAGE_TAG must not be 'latest' or 'dev'. Got: $IMAGE_TAG" >&2
  exit 1
fi

# Validate environment
if [ "$ENVIRONMENT" != "staging" ] && [ "$ENVIRONMENT" != "production" ]; then
  echo "ERROR: ENVIRONMENT must be 'staging' or 'production'. Got: $ENVIRONMENT" >&2
  exit 1
fi

# ── Paths ─────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GITOPS_DIR="$PROJECT_DIR/gitops/$ENVIRONMENT"
RENDER_SCRIPT="$SCRIPT_DIR/render-k8s-manifests.sh"

# ── Render ────────────────────────────────────────────────────────────────
echo "=== Updating GitOps manifests for $ENVIRONMENT ==="
echo "  REGISTRY:   $REGISTRY"
echo "  IMAGE_TAG:  $IMAGE_TAG"
echo "  GITOPS_DIR: $GITOPS_DIR"

# Step 1: Render manifests
REGISTRY="$REGISTRY" IMAGE_TAG="$IMAGE_TAG" "$RENDER_SCRIPT" "$ENVIRONMENT"

RENDERED_DIR="$PROJECT_DIR/build/k8s/$ENVIRONMENT"
if [ ! -d "$RENDERED_DIR" ]; then
  echo "ERROR: Rendered manifests not found at $RENDERED_DIR" >&2
  exit 1
fi

# Step 2: Sync to GitOps directory
echo "Syncing to $GITOPS_DIR ..."

# Remove old YAML files (preserve README.md and other non-yaml files)
find "$GITOPS_DIR" -name "*.yaml" -type f -delete 2>/dev/null || true

# Copy rendered manifests
cp "$RENDERED_DIR"/*.yaml "$GITOPS_DIR/" 2>/dev/null || true

# Step 3: Final validation on GitOps output
echo "Validating GitOps output..."

# Check no :latest or :dev in image: lines
if grep -E '^\s*image:.*:latest|^\s*image:.*:dev' "$GITOPS_DIR"/*.yaml 2>/dev/null; then
  echo "ERROR: GitOps manifests contain ':latest' or ':dev' image tags!" >&2
  exit 1
fi

# Check securityContext in deployments
for deploy in "$GITOPS_DIR"/deployment*.yaml; do
  [ -f "$deploy" ] || continue
  if ! grep -q "runAsNonRoot: true" "$deploy"; then
    echo "WARNING: $deploy missing runAsNonRoot"
  fi
done

# Check NetworkPolicy exists
if [ ! -f "$GITOPS_DIR/networkpolicy-sandbox-worker.yaml" ]; then
  echo "WARNING: sandbox-worker NetworkPolicy not found in GitOps manifests"
fi
if [ ! -f "$GITOPS_DIR/networkpolicy-api-egress.yaml" ]; then
  echo "WARNING: platform-api egress NetworkPolicy not found in GitOps manifests"
fi
if [ ! -f "$GITOPS_DIR/networkpolicy-render-worker-egress.yaml" ]; then
  echo "WARNING: render-worker egress NetworkPolicy not found in GitOps manifests"
fi
if [ ! -f "$GITOPS_DIR/networkpolicy-egress-proxy.yaml" ]; then
  echo "WARNING: egress-proxy NetworkPolicy not found in GitOps manifests"
fi
if [ ! -f "$GITOPS_DIR/deployment-egress-proxy.yaml" ]; then
  echo "WARNING: egress-proxy Deployment not found in GitOps manifests"
fi

# Count resources
RESOURCE_COUNT=$(ls "$GITOPS_DIR"/*.yaml 2>/dev/null | wc -l)
echo "Synced $RESOURCE_COUNT resources to $GITOPS_DIR"

echo "Done."
