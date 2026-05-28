#!/usr/bin/env bash
# render-k8s-manifests.sh — Render K8s manifests for a specific environment.
#
# Usage:
#   REGISTRY=registry.example.com IMAGE_TAG=git-abc1234 ./scripts/render-k8s-manifests.sh staging
#   REGISTRY=registry.example.com IMAGE_TAG=v1.2.3 ./scripts/render-k8s-manifests.sh production
#
# Requirements:
#   - REGISTRY env var set
#   - IMAGE_TAG env var set (must not be "latest" or "dev")
#
# Output:
#   build/k8s/<environment>/*.yaml

set -euo pipefail

# ── Validate inputs ──────────────────────────────────────────────────────
ENVIRONMENT="${1:?Usage: $0 <staging|production>}"
REGISTRY="${REGISTRY:?REGISTRY is required, e.g. REGISTRY=registry.example.com}"
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
OVERLAY_DIR="$PROJECT_DIR/k8s/overlays/$ENVIRONMENT"
OUTPUT_DIR="$PROJECT_DIR/build/k8s/$ENVIRONMENT"

# ── Render ────────────────────────────────────────────────────────────────
echo "Rendering K8s manifests for environment: $ENVIRONMENT"
echo "  REGISTRY:   $REGISTRY"
echo "  IMAGE_TAG:  $IMAGE_TAG"
echo "  OVERLAY:    $OVERLAY_DIR"
echo "  OUTPUT:     $OUTPUT_DIR"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Copy base manifests and apply environment-specific transformations
for f in "$PROJECT_DIR"/k8s/base/*.yaml; do
  filename="$(basename "$f")"
  dest="$OUTPUT_DIR/$filename"

  # Replace image placeholders with actual registry and tag
  # Match any image: <name>:dev or image: <name>:latest and replace with registry/tag
  sed \
    -e "s|image: platform-api:dev|image: ${REGISTRY}/platform-api:${IMAGE_TAG}|g" \
    -e "s|image: platform-api:latest|image: ${REGISTRY}/platform-api:${IMAGE_TAG}|g" \
    -e "s|image: platform-render-worker:dev|image: ${REGISTRY}/platform-render-worker:${IMAGE_TAG}|g" \
    -e "s|image: platform-render-worker:latest|image: ${REGISTRY}/platform-render-worker:${IMAGE_TAG}|g" \
    -e "s|image: media-platform/sandbox-worker:dev|image: ${REGISTRY}/platform-sandbox-worker:${IMAGE_TAG}|g" \
    -e "s|image: media-platform/sandbox-worker:latest|image: ${REGISTRY}/platform-sandbox-worker:${IMAGE_TAG}|g" \
    -e "s|image: ubuntu/squid:6.6-22.04_stable|image: ${REGISTRY}/egress-proxy:${IMAGE_TAG}|g" \
    -e "s|namespace: media-platform|namespace: media-platform-${ENVIRONMENT}|g" \
    -e "s|name: media-platform|name: media-platform-${ENVIRONMENT}|g" \
    "$f" > "$dest"
done

# Apply environment-specific patches
if [ "$ENVIRONMENT" = "staging" ]; then
  # Staging: smaller resources, single replica
  sed -i \
    -e 's/replicas: 2/replicas: 1/g' \
    -e 's/memory: "512Mi"/memory: "256Mi"/g' \
    -e 's/memory: "2Gi"/memory: "1Gi"/g' \
    -e 's/memory: "4Gi"/memory: "2Gi"/g' \
    -e 's/cpu: "250m"/cpu: "125m"/g' \
    -e 's/cpu: "1000m"/cpu: "500m"/g' \
    -e 's/cpu: "2000m"/cpu: "1000m"/g' \
    -e 's|host: staging.api.example.com|host: staging-api.example.com|g' \
    "$OUTPUT_DIR/deployment-api.yaml" 2>/dev/null || true

  sed -i \
    -e 's/replicas: 2/replicas: 1/g' \
    "$OUTPUT_DIR/deployment-render-worker.yaml" 2>/dev/null || true
fi

if [ "$ENVIRONMENT" = "production" ]; then
  # Production: ensure dev-only features are disabled
  for f in "$OUTPUT_DIR"/*.yaml; do
    sed -i \
      -e 's/APP_SECURITY_DEV_AUTH_ENDPOINT: "true"/APP_SECURITY_DEV_AUTH_ENDPOINT: "false"/g' \
      -e 's/APP_SECURITY_OIDC_DEV_BOOTSTRAP: "true"/APP_SECURITY_OIDC_DEV_BOOTSTRAP: "false"/g' \
      -e 's/SANDBOX_EXECUTION_MODE: "in-process"/SANDBOX_EXECUTION_MODE: "external"/g' \
      "$f" 2>/dev/null || true
  done

  sed -i \
    -e 's|host: api.media-platform.example.com|host: api.example.com|g' \
    "$OUTPUT_DIR/ingress.yaml" 2>/dev/null || true
fi

# ── Post-render validation ───────────────────────────────────────────────
echo "Validating rendered manifests..."

# Check no :latest or :dev in image: lines (allow comments mentioning them)
if grep -E '^\s*image:.*:latest|^\s*image:.*:dev' "$OUTPUT_DIR"/*.yaml 2>/dev/null; then
  echo "ERROR: Rendered manifests contain ':latest' or ':dev' image tags!" >&2
  grep -nE '^\s*image:.*:latest|^\s*image:.*:dev' "$OUTPUT_DIR"/*.yaml >&2
  exit 1
fi

# Check securityContext exists in deployments
for deploy in "$OUTPUT_DIR"/deployment*.yaml; do
  [ -f "$deploy" ] || continue
  if ! grep -q "runAsNonRoot: true" "$deploy"; then
    echo "WARNING: $deploy missing runAsNonRoot"
  fi
done

# Check NetworkPolicy exists
if [ ! -f "$OUTPUT_DIR/networkpolicy-sandbox-worker.yaml" ]; then
  echo "WARNING: sandbox-worker NetworkPolicy not found in rendered manifests"
fi
if [ ! -f "$OUTPUT_DIR/networkpolicy-api-egress.yaml" ]; then
  echo "WARNING: platform-api egress NetworkPolicy not found in rendered manifests"
fi
if [ ! -f "$OUTPUT_DIR/networkpolicy-render-worker-egress.yaml" ]; then
  echo "WARNING: render-worker egress NetworkPolicy not found in rendered manifests"
fi
if [ ! -f "$OUTPUT_DIR/networkpolicy-egress-proxy.yaml" ]; then
  echo "WARNING: egress-proxy NetworkPolicy not found in rendered manifests"
fi
if [ ! -f "$OUTPUT_DIR/deployment-egress-proxy.yaml" ]; then
  echo "WARNING: egress-proxy Deployment not found in rendered manifests"
fi

# Count resources
RESOURCE_COUNT=$(ls "$OUTPUT_DIR"/*.yaml 2>/dev/null | wc -l)
echo "Rendered $RESOURCE_COUNT resources to $OUTPUT_DIR"

echo "Done."
