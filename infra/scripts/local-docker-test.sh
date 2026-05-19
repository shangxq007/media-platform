#!/usr/bin/env bash
# scripts/local-docker-test.sh
# Validates local Docker Compose configuration and runs smoke tests.
#
# Usage: bash scripts/local-docker-test.sh
#
# Requirements:
#   - Docker and Docker Compose
#   - curl
#
# This script does NOT push images or connect to production resources.

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
CONTAINER_NAME="media-platform-app"
DB_CONTAINER="media-platform-db"
MAX_WAIT_SECONDS=120

# ─── Step 1: Validate Docker Compose config ───────────────────────────────
log_info "Step 1: Validating docker compose config..."
if ! docker compose -f "${COMPOSE_FILE}" config > /dev/null 2>&1; then
    log_error "docker compose config is invalid!"
    docker compose -f "${COMPOSE_FILE}" config
    exit 1
fi
log_info "Docker compose config is valid."

# ─── Step 2: Validate .env.example exists ─────────────────────────────────
log_info "Step 2: Checking .env.example..."
if [ ! -f "${PROJECT_ROOT}/.env.example" ]; then
    log_error ".env.example not found!"
    exit 1
fi
log_info ".env.example found."

# ─── Step 3: Check that .env is gitignored ────────────────────────────────
log_info "Step 3: Verifying .env is gitignored..."
if git check-ignore -q .env 2>/dev/null; then
    log_info ".env is properly gitignored."
else
    log_warn ".env is NOT gitignored — adding entry."
    echo ".env" >> .gitignore
fi

# ─── Step 4: Start services ───────────────────────────────────────────────
log_info "Step 4: Starting Docker Compose services..."
docker compose -f "${COMPOSE_FILE}" down -v 2>/dev/null || true
docker compose -f "${COMPOSE_FILE}" up -d --build

# ─── Step 5: Wait for app to be healthy ───────────────────────────────────
log_info "Step 5: Waiting for app to become healthy (max ${MAX_WAIT_SECONDS}s)..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT_SECONDS ]; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        log_info "App is healthy (HTTP 200)."
        break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo -n "."
done

if [ $elapsed -ge $MAX_WAIT_SECONDS ]; then
    log_error "App did not become healthy within ${MAX_WAIT_SECONDS}s."
    docker compose -f "${COMPOSE_FILE}" logs app
    docker compose -f "${COMPOSE_FILE}" down -v
    exit 1
fi

# ─── Step 6: Smoke tests ──────────────────────────────────────────────────
log_info "Step 6: Running smoke tests..."

PASS=0
FAIL=0

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

# Health / actuator
smoke_test "Health endpoint" "200" "http://localhost:8080/actuator/health"
smoke_test "Info endpoint" "200" "http://localhost:8080/actuator/info"
smoke_test "Metrics endpoint" "200" "http://localhost:8080/actuator/metrics"

# API (may return 401/403 without auth, but should not 500)
smoke_test "Analytics events endpoint exists" "405" "http://localhost:8080/api/v1/analytics/events"
smoke_test "Profiles endpoint exists" "405" "http://localhost:8080/api/v1/analytics/profiles"

# ─── Step 7: Cleanup ──────────────────────────────────────────────────────
log_info "Step 7: Shutting down Docker Compose services..."
docker compose -f "${COMPOSE_FILE}" down -v

# ─── Summary ──────────────────────────────────────────────────────────────
echo ""
log_info "Smoke test results: ${PASS} passed, ${FAIL} failed"
if [ $FAIL -gt 0 ]; then
    log_error "Some smoke tests failed!"
    exit 1
fi
log_info "All smoke tests passed!"
