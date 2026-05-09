#!/usr/bin/env bash
# End-to-End Render Flow Smoke Test
# Usage: ./scripts/smoke/e2e-render-flow.sh [base_url]
# Default: http://localhost:8080
#
# Tests the full business chain:
#   health -> create tenant -> create project -> create render job
#   -> execute local -> query job -> query artifacts -> query notifications
#   -> query audit -> query outbox

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
TOTAL=0

# Colors
if [[ -t 1 ]]; then
  GREEN='\033[0;32m'
  RED='\033[0;31m'
  YELLOW='\033[0;33m'
  CYAN='\033[0;36m'
  RESET='\033[0m'
else
  GREEN='' RED='' YELLOW='' CYAN='' RESET=''
fi

###############################################################################
# check — HTTP status assertion
###############################################################################
check() {
  local name="$1"
  local url="$2"
  local expected="${3:-200}"
  local method="${4:-GET}"
  local body="${5:-}"
  TOTAL=$((TOTAL + 1))

  local http_code
  local curl_args=(-sS -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 15)

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
# check_body — HTTP 200 + body contains string
###############################################################################
check_body() {
  local name="$1"
  local url="$2"
  local needle="$3"
  local method="${4:-GET}"
  local body="${5:-}"
  TOTAL=$((TOTAL + 1))

  local response
  local curl_args=(-sS --connect-timeout 5 --max-time 15)

  if [[ "$method" == "POST" && -n "$body" ]]; then
    curl_args+=(-X POST -H "Content-Type: application/json" -d "$body")
  fi

  response=$(curl "${curl_args[@]}" "$url" 2>/dev/null) || true

  if echo "$response" | grep -q "$needle"; then
    echo -e "  ${GREEN}PASS${RESET}  $name"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${RESET}  $name  (response did not contain: $needle)"
    FAIL=$((FAIL + 1))
  fi
}

###############################################################################
# extract_json — extract a JSON field value (simple, no jq dependency)
###############################################################################
extract_json() {
  local json="$1"
  local field="$2"
  echo "$json" | grep -o "\"$field\"[^,}]*" | head -1 | sed 's/.*"'"$field"'"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/'
}

###############################################################################
# Main
###############################################################################
echo "========================================="
echo "  E2E Render Flow Smoke Test"
echo "  Base URL: $BASE_URL"
echo "========================================="
echo ""

# --- Health Check ---
echo -e "${CYAN}--- Step 1: Health Check ---${RESET}"
check "Health" "$BASE_URL/actuator/health" 200
echo ""

# --- Create Tenant ---
echo -e "${CYAN}--- Step 2: Create Tenant ---${RESET}"
TENANT_BODY='{"name":"E2E Render Flow Tenant"}'
check "Create tenant" "$BASE_URL/api/v1/identity/tenants" 200 POST "$TENANT_BODY"

TENANT_RESPONSE=$(curl -sS -X POST -H "Content-Type: application/json" \
  -d "$TENANT_BODY" "$BASE_URL/api/v1/identity/tenants" 2>/dev/null) || true
TENANT_ID=$(extract_json "$TENANT_RESPONSE" "id")
echo -e "  ${YELLOW}INFO${RESET}  Tenant ID: ${TENANT_ID:-unknown}"
echo ""

# --- Create Project ---
echo -e "${CYAN}--- Step 3: Create Project ---${RESET}"
PROJECT_BODY='{"name":"E2E Project","description":"End-to-end render flow test"}'
check "Create project" "$BASE_URL/api/v1/identity/tenants/${TENANT_ID}/projects" 200 POST "$PROJECT_BODY"

PROJECT_RESPONSE=$(curl -sS -X POST -H "Content-Type: application/json" \
  -d "$PROJECT_BODY" "$BASE_URL/api/v1/identity/tenants/${TENANT_ID}/projects" 2>/dev/null) || true
PROJECT_ID=$(extract_json "$PROJECT_RESPONSE" "id")
echo -e "  ${YELLOW}INFO${RESET}  Project ID: ${PROJECT_ID:-unknown}"
echo ""

# --- Create API Key ---
echo -e "${CYAN}--- Step 4: Create API Key ---${RESET}"
APIKEY_BODY='{"principal":"e2e-service"}'
check "Create API key" "$BASE_URL/api/v1/identity/tenants/${TENANT_ID}/apikeys" 200 POST "$APIKEY_BODY"
echo ""

# --- Create Render Job ---
echo -e "${CYAN}--- Step 5: Create Render Job ---${RESET}"
RENDER_BODY='{"projectId":"'"${PROJECT_ID}"'","timelineSnapshotId":"snap_e2e_001","profile":"default_1080p"}'
check "Create render job" "$BASE_URL/api/v1/tenants/${TENANT_ID}/projects/${PROJECT_ID}/render-jobs" 200 POST "$RENDER_BODY"

JOB_RESPONSE=$(curl -sS -X POST -H "Content-Type: application/json" \
  -d "$RENDER_BODY" "$BASE_URL/api/v1/tenants/${TENANT_ID}/projects/${PROJECT_ID}/render-jobs" 2>/dev/null) || true
JOB_ID=$(extract_json "$JOB_RESPONSE" "id")
echo -e "  ${YELLOW}INFO${RESET}  Render Job ID: ${JOB_ID:-unknown}"
echo ""

# --- Query Render Job ---
echo -e "${CYAN}--- Step 6: Query Render Job ---${RESET}"
check_body "Get render job" "$BASE_URL/api/v1/tenants/${TENANT_ID}/projects/${PROJECT_ID}/render-jobs/${JOB_ID}" "id"
echo ""

# --- Execute Local Workflow ---
echo -e "${CYAN}--- Step 7: Execute Local Workflow ---${RESET}"
check "Execute local" "$BASE_URL/api/v1/tenants/${TENANT_ID}/projects/${PROJECT_ID}/render-jobs/${JOB_ID}/execute-local" 200 POST
echo ""

# --- Query Execution Status ---
echo -e "${CYAN}--- Step 8: Query Execution Status ---${RESET}"
check_body "Get execution" "$BASE_URL/api/v1/tenants/${TENANT_ID}/projects/${PROJECT_ID}/render-jobs/${JOB_ID}/execution" "status"
echo ""

# --- Query Quota ---
echo -e "${CYAN}--- Step 9: Query Quota ---${RESET}"
check_body "Get quota" "$BASE_URL/api/v1/tenants/${TENANT_ID}/quota" "tenantId"
echo ""

# --- Query Entitlements ---
echo -e "${CYAN}--- Step 10: Query Entitlements ---${RESET}"
check_body "Get entitlements" "$BASE_URL/api/v1/tenants/${TENANT_ID}/entitlements" "tenantId"
echo ""

# --- Query Notifications ---
echo -e "${CYAN}--- Step 11: Query Notifications ---${RESET}"
check "Get notifications" "$BASE_URL/api/v1/tenants/${TENANT_ID}/notifications" 200
echo ""

# --- Query Audit Records ---
echo -e "${CYAN}--- Step 12: Query Audit Records ---${RESET}"
check "Audit overview" "$BASE_URL/api/v1/audit/compliance/overview" 200
echo ""

# --- Query Outbox ---
echo -e "${CYAN}--- Step 13: Query Outbox ---${RESET}"
check "Outbox overview" "$BASE_URL/api/v1/outbox/overview" 200
echo ""

# --- Summary ---
echo "========================================="
echo "  Results: $PASS passed, $FAIL failed, $TOTAL total"
echo "========================================="

if [[ "$FAIL" -gt 0 ]]; then
  echo -e "  ${RED}E2E RENDER FLOW FAILED${RESET}"
  exit 1
else
  echo -e "  ${GREEN}E2E RENDER FLOW PASSED${RESET}"
  exit 0
fi
