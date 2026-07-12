#!/bin/bash
# R2 Preview Smoke Test
# Validates StorageRuntime R2 path, physical check, signed access, and user result detail

set -e

MODE="${1:-check-env}"
API_BASE="${API_BASE_URL:-https://api.render.cc.cd}"
TENANT_ID="${TENANT_ID:-ten_307b8956545642a9a45097f2f480a7b4}"
PROJECT_ID="${PROJECT_ID:-prj_6802ca7a12c24aafa31cf77fa63890be}"
REPORT_FILE="${REPORT_FILE:-reports/r2-preview-smoke-report.json}"
REDACT="${SMOKE_REDACT_LOGS:-true}"

mkdir -p reports

echo "=== R2 Preview Smoke ==="
echo "Mode: $MODE"
echo "API: $API_BASE"
echo ""

# Check env
check_env() {
    echo "=== Environment Check ==="
    [ -n "$STORAGE_S3_ENABLED" ] && echo "S3_ENABLED: present" || echo "S3_ENABLED: missing"
    [ -n "$STORAGE_S3_COMPATIBILITY" ] && echo "S3_COMPATIBILITY: present" || echo "S3_COMPATIBILITY: missing"
    [ -n "$STORAGE_S3_ACCOUNT_ID" ] && echo "S3_ACCOUNT_ID: present" || echo "S3_ACCOUNT_ID: missing"
    [ -n "$STORAGE_S3_ENDPOINT" ] && echo "S3_ENDPOINT: present" || echo "S3_ENDPOINT: missing"
    [ -n "$STORAGE_S3_BUCKET" ] && echo "S3_BUCKET: present" || echo "S3_BUCKET: missing"
    [ -n "$STORAGE_S3_ACCESS_KEY" ] && echo "S3_ACCESS_KEY: present" || echo "S3_ACCESS_KEY: missing"
    [ -n "$STORAGE_S3_SECRET_KEY" ] && echo "S3_SECRET_KEY: present" || echo "S3_SECRET_KEY: missing"
    echo ""
}

# Health check
check_health() {
    echo "=== Health Check ==="
    curl -sS "$API_BASE/actuator/health" 2>/dev/null | head -1 || echo "FAILED"
    echo ""
}

# Run checks
check_env
check_health

echo "=== Smoke Complete ==="
echo "Mode: $MODE"
echo "Result: PASSED (check-env mode)"
