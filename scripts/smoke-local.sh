#!/usr/bin/env bash
# Smoke test for local media platform
# Usage: ./scripts/smoke-local.sh [base_url]
# Default: http://localhost:8080
#
# Requirements: curl only. No other dependencies.
# Safe and local-only — uses GET except for one POST to audit (idempotent read-after-write).

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
TOTAL=0

# Colors (fallback gracefully if not a tty)
if [[ -t 1 ]]; then
  GREEN='\033[0;32m'
  RED='\033[0;31m'
  YELLOW='\033[0;33m'
  RESET='\033[0m'
else
  GREEN=''
  RED=''
  YELLOW=''
  RESET=''
fi

###############################################################################
# check — HTTP status assertion
#   $1  name      (e.g. "Health")
#   $2  url       (e.g. "$BASE_URL/actuator/health")
#   $3  expected  (e.g. 200, default 200)
#   $4  method    (e.g. GET, POST, default GET)
#   $5  body      (JSON body for POST, optional)
###############################################################################
check() {
  local name="$1"
  local url="$2"
  local expected="${3:-200}"
  local method="${4:-GET}"
  local body="${5:-}"
  TOTAL=$((TOTAL + 1))

  local http_code
  local curl_args=(-sS -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10)

  if [[ "$method" == "POST" && -n "$body" ]]; then
    curl_args+=(-X POST -H "Content-Type: application/json" -d "$body")
  fi

  http_code=$(curl "${curl_args[@]}" "$url" 2>/dev/null) || true

  if [[ "$http_code" == "$expected" ]]; then
    echo -e "  ${GREEN}PASS${RESET}  [$http_code]  $name"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${RESET}  [$http_code]  $name  (expected $expected)"
    FAIL=$((FAIL + 1))
  fi
}

###############################################################################
# check_running — bail out early if the app is not reachable
###############################################################################
check_running() {
  echo "Checking if application is reachable at $BASE_URL ..."
  if curl -sS --connect-timeout 3 --max-time 5 -o /dev/null "$BASE_URL/actuator/health" 2>/dev/null; then
    echo -e "  ${GREEN}Application is running.${RESET}"
  else
    echo -e "  ${RED}Cannot reach $BASE_URL — is the application started?${RESET}"
    echo "  Start with: ./gradlew :platform-app:bootRun"
    exit 1
  fi
  echo ""
}

###############################################################################
# Main
###############################################################################
echo "========================================="
echo "  Media Platform — Local Smoke Test"
echo "  Base URL: $BASE_URL"
echo "========================================="
echo ""

check_running

# --- Health ---
echo "--- Health ---"
check "Health" "$BASE_URL/actuator/health" 200
echo ""

# --- OpenAPI ---
echo "--- OpenAPI ---"
check "OpenAPI public-v1 docs" "$BASE_URL/v3/api-docs/public-v1" 200
check "OpenAPI actuator docs"  "$BASE_URL/v3/api-docs/actuator"  200
echo ""

# --- P0: Core Cross-Cutting Capabilities ---
echo "--- P0: Cross-Cutting Capabilities ---"
check "Observability overview"  "$BASE_URL/api/v1/observability/overview"  200
check "Audit overview"          "$BASE_URL/api/v1/audit/compliance/overview"  200
check "Outbox overview"         "$BASE_URL/api/v1/outbox/event/overview"  200
echo ""

# --- P1: Domain Endpoints ---
echo "--- P1: Domain Endpoints ---"
check "Render jobs list"        "$BASE_URL/api/v1/render-jobs"  200
check "Config list"             "$BASE_URL/api/v1/config"  200
check "Identity overview"       "$BASE_URL/api/v1/identity/access/overview"  200
check "Policy overview"         "$BASE_URL/api/v1/policy/overview"  200
echo ""

# --- P1: Write Operations (safe, local-only) ---
echo "--- P1: Write Operations ---"

AUDIT_BODY='{"actorType":"smoke-test","actorId":"smoke-001","action":"smoke.test","resourceType":"SmokeTest","resourceId":"smoke-001","payload":{"source":"smoke-local.sh"}}'
check "Audit create record" "$BASE_URL/api/v1/audit/compliance/records" 200 POST "$AUDIT_BODY"

RENDER_BODY='{"projectId":"smoke-p1","timelineSnapshotId":"smoke-tl1","profile":"default"}'
check "Render create job" "$BASE_URL/api/v1/render-jobs" 200 POST "$RENDER_BODY"
echo ""

# --- Summary ---
echo "========================================="
echo "  Results: $PASS passed, $FAIL failed, $TOTAL total"
echo "========================================="

if [[ "$FAIL" -gt 0 ]]; then
  echo -e "  ${RED}SMOKE TEST FAILED${RESET}"
  exit 1
else
  echo -e "  ${GREEN}ALL CHECKS PASSED${RESET}"
  exit 0
fi
