#!/usr/bin/env bash
# scripts/local-test.sh
# Local smoke test for the media-platform application.
#
# This script starts the application with Docker Compose (PostgreSQL),
# waits for it to become healthy, runs smoke tests, then shuts down.
#
# Usage: bash scripts/local-test.sh
#
# Requirements:
#   - Docker and Docker Compose
#   - curl
#   - jq (optional, for JSON parsing)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
COMPOSE="docker compose -f ${COMPOSE_FILE}"
MAX_WAIT_SECONDS=120
PASS=0
FAIL=0

cleanup() {
    log_info "Cleaning up Docker Compose..."
    ${COMPOSE} down -v 2>/dev/null || true
}
trap cleanup EXIT

smoke_test() {
    local name="$1"
    local expected_code="$2"
    local url="$3"
    local actual_code
    actual_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    if [ "$actual_code" = "$expected_code" ]; then
        log_info "  PASS: ${name} (HTTP ${actual_code})"
        PASS=$((PASS + 1))
    else
        log_error "  FAIL: ${name} (expected HTTP ${expected_code}, got HTTP ${actual_code})"
        FAIL=$((FAIL + 1))
    fi
}

# ─── Step 1: Validate config ──────────────────────────────────────────
log_info "Step 1: Validating docker compose config..."
if ! ${COMPOSE} config > /dev/null 2>&1; then
    log_error "docker compose config is invalid!"
    exit 1
fi
log_info "Config valid."

# ─── Step 2: Start services ───────────────────────────────────────────
log_info "Step 2: Starting services..."
${COMPOSE} down -v 2>/dev/null || true
${COMPOSE} up -d --build

# ─── Step 3: Wait for health ──────────────────────────────────────────
log_info "Step 3: Waiting for app to become healthy (max ${MAX_WAIT_SECONDS}s)..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT_SECONDS ]; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        log_info "App is healthy."
        break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo -n "."
done

if [ $elapsed -ge $MAX_WAIT_SECONDS ]; then
    log_error "App did not become healthy within ${MAX_WAIT_SECONDS}s."
    ${COMPOSE} logs app
    exit 1
fi

# ─── Step 4: Smoke tests ──────────────────────────────────────────────
log_info "Step 4: Running smoke tests..."

# Health / actuator
smoke_test "Health endpoint" "200" "http://localhost:8080/actuator/health"
smoke_test "Info endpoint" "200" "http://localhost:8080/actuator/info"

# Analytics API
smoke_test "Analytics events endpoint" "405" "http://localhost:8080/api/v1/analytics/events"
smoke_test "Analytics profiles endpoint" "405" "http://localhost:8080/api/v1/analytics/profiles"
smoke_test "Analytics segments endpoint" "405" "http://localhost:8080/api/v1/analytics/segments"
smoke_test "Analytics rebuild-profiles endpoint" "405" "http://localhost:8080/api/v1/analytics/internal/rebuild-profiles"
smoke_test "Analytics rebuild-segments endpoint" "405" "http://localhost:8080/api/v1/analytics/internal/rebuild-segments"

# Commerce API
smoke_test "Commerce checkout endpoint" "405" "http://localhost:8080/api/v1/commerce/checkout-sessions"

# ─── Step 5: Functional test ──────────────────────────────────────────
log_info "Step 5: Running functional tests..."

# Ingest an event
EVENT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/analytics/events \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"userId":"user-1","eventType":"page_view","action":"view","resourceType":"dashboard","metadata":{"page":"home"}}')

if echo "$EVENT_RESPONSE" | grep -q '"eventId"'; then
    log_info "  PASS: Event ingestion"
    PASS=$((PASS + 1))
else
    log_error "  FAIL: Event ingestion — response: $EVENT_RESPONSE"
    FAIL=$((FAIL + 1))
fi

# Query profile
PROFILE_RESPONSE=$(curl -s http://localhost:8080/api/v1/analytics/profiles/user-1 \
  -H "X-Tenant-ID: tenant-1")

if echo "$PROFILE_RESPONSE" | grep -q '"profileId"'; then
    log_info "  PASS: Profile query"
    PASS=$((PASS + 1))
else
    log_error "  FAIL: Profile query — response: $PROFILE_RESPONSE"
    FAIL=$((FAIL + 1))
fi

# Compute segment
SEGMENT_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/analytics/segments/active?activeWithinDays=30" \
  -H "X-Tenant-ID: tenant-1")

if echo "$SEGMENT_RESPONSE" | grep -q '"segmentId"'; then
    log_info "  PASS: Segment computation"
    PASS=$((PASS + 1))
else
    log_error "  FAIL: Segment computation — response: $SEGMENT_RESPONSE"
    FAIL=$((FAIL + 1))
fi

# Trigger rebuild
REBUILD_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/analytics/internal/rebuild-profiles \
  -H "X-Tenant-ID: tenant-1")

if echo "$REBUILD_RESPONSE" | grep -q '"status"'; then
    log_info "  PASS: Profile rebuild trigger"
    PASS=$((PASS + 1))
else
    log_error "  FAIL: Profile rebuild trigger — response: $REBUILD_RESPONSE"
    FAIL=$((FAIL + 1))
fi

# ─── Summary ──────────────────────────────────────────────────────────
echo ""
log_info "Smoke test results: ${PASS} passed, ${FAIL} failed"
if [ $FAIL -gt 0 ]; then
    log_error "Some smoke tests failed!"
    exit 1
fi
log_info "All smoke tests passed!"
