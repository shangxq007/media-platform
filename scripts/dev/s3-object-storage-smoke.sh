#!/usr/bin/env bash
# S3-compatible object storage smoke test for local development.
# Verifies RustFS-backed dev object storage is running and S3 API works.
#
# Usage: ./scripts/dev/s3-object-storage-smoke.sh
#
# Prerequisites:
#   docker compose --profile s3 up -d
#   aws cli installed
#
# This script uses dev-only credentials and targets localhost:9000.
# No production credentials or secrets are required.

set -euo pipefail

ENDPOINT="${S3_ENDPOINT:-http://localhost:9000}"
BUCKET="${S3_SMOKE_BUCKET:-media-platform-dev}"
ACCESS_KEY="${RUSTFS_ACCESS_KEY:-dev-access-key}"
SECRET_KEY="${RUSTFS_SECRET_KEY:-dev-secret-key}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION="$REGION"

SMOKE_FILE="/tmp/media-platform-rustfs-smoke-$$.txt"
SMOKE_OUT="/tmp/media-platform-rustfs-smoke-$$.out"
SMOKE_KEY="smoke/s3-smoke-$(date +%s).txt"

cleanup() {
  rm -f "$SMOKE_FILE" "$SMOKE_OUT"
  aws --endpoint-url "$ENDPOINT" s3 rm "s3://$BUCKET/$SMOKE_KEY" 2>/dev/null || true
}
trap cleanup EXIT

echo "=== S3 Object Storage Smoke ==="
echo "Endpoint: $ENDPOINT"
echo "Bucket:   $BUCKET"
echo ""

# Check AWS CLI
if ! command -v aws &>/dev/null; then
  echo "FAIL: aws CLI not found. Install with: brew install awscli (macOS) or apt install awscli (Ubuntu)"
  exit 1
fi

# Check connectivity
echo "1. Checking connectivity..."
if ! curl -sf "$ENDPOINT/health/live" >/dev/null 2>&1; then
  echo "FAIL: Cannot reach $ENDPOINT/health/live"
  echo "      Start object storage: docker compose -f docker-compose.dev.yml --profile s3 up -d"
  exit 1
fi
echo "   OK: Service is healthy"

# Create bucket (idempotent)
echo "2. Ensuring bucket '$BUCKET' exists..."
aws --endpoint-url "$ENDPOINT" s3 mb "s3://$BUCKET" 2>/dev/null || true
echo "   OK: Bucket ready"

# Put object
echo "3. Uploading test object..."
echo "hello rustfs smoke $(date)" > "$SMOKE_FILE"
aws --endpoint-url "$ENDPOINT" s3 cp "$SMOKE_FILE" "s3://$BUCKET/$SMOKE_KEY" 2>&1
echo "   OK: Uploaded $SMOKE_KEY"

# Head object
echo "4. Checking object metadata..."
aws --endpoint-url "$ENDPOINT" s3api head-object --bucket "$BUCKET" --key "$SMOKE_KEY" >/dev/null 2>&1
echo "   OK: head-object succeeded"

# Get object
echo "5. Downloading object..."
aws --endpoint-url "$ENDPOINT" s3 cp "s3://$BUCKET/$SMOKE_KEY" "$SMOKE_OUT" 2>&1
echo "   OK: Downloaded"

# Verify content
echo "6. Verifying content..."
if diff -q "$SMOKE_FILE" "$SMOKE_OUT" >/dev/null 2>&1; then
  echo "   OK: Content matches"
else
  echo "FAIL: Downloaded content does not match uploaded content"
  exit 1
fi

# Remove object
echo "7. Cleaning up test object..."
aws --endpoint-url "$ENDPOINT" s3 rm "s3://$BUCKET/$SMOKE_KEY" 2>&1
echo "   OK: Removed"

echo ""
echo "=== S3 SMOKE PASSED ==="
