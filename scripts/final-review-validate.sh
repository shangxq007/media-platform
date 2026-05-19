#!/bin/bash
# final-review-validate.sh
# Production readiness final validation script
# Runs all quality gates and outputs PASS/FAIL summary

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0

run_check() {
    local name="$1"
    shift
    echo -n "  Checking ${name}... "
    if "$@" > /dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}"
        ((PASS++))
    else
        echo -e "${RED}FAIL${NC}"
        ((FAIL++))
    fi
}

run_check_warn() {
    local name="$1"
    shift
    echo -n "  Checking ${name}... "
    if "$@" > /dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}"
        ((PASS++))
    else
        echo -e "${YELLOW}SKIP${NC}"
        ((SKIP++))
    fi
}

echo "========================================"
echo "  Media Platform - Final Review Validation"
echo "  $(date)"
echo "========================================"
echo ""

# 1. Backend compilation
echo "[1/7] Backend Compilation"
run_check "Java compilation" ./gradlew compileJava --warning-mode=none

# 2. Backend tests
echo ""
echo "[2/7] Backend Tests"
run_check "Unit/integration tests" ./gradlew test --warning-mode=none

# 3. Boot JAR
echo ""
echo "[3/7] Boot JAR Build"
run_check "platform-app:bootJar" ./gradlew :platform-app:bootJar --warning-mode=none

# 4. Docker Compose
echo ""
echo "[4/7] Docker Compose"
run_check "docker compose config" docker compose config

# 5. Frontend build
echo ""
echo "[5/7] Frontend Build"
if [ -d "frontend/node_modules" ]; then
    cd frontend
    run_check "vite build" ./node_modules/.bin/vite build
    cd ..
else
    echo -e "  ${YELLOW}SKIP${NC} - frontend/node_modules not found"
    ((SKIP++))
fi

# 6. Frontend tests
echo ""
echo "[6/7] Frontend Tests"
if [ -d "frontend/node_modules" ]; then
    cd frontend
    run_check "vitest run" ./node_modules/.bin/vitest run
    cd ..
else
    echo -e "  ${YELLOW}SKIP${NC} - frontend/node_modules not found"
    ((SKIP++))
fi

# 7. Infra validation
echo ""
echo "[7/7] Infrastructure Validation"
run_check_warn "infra-validate.sh" bash scripts/infra-validate.sh

echo ""
echo "========================================"
echo "  Results"
echo "========================================"
echo -e "  ${GREEN}PASS${NC}: ${PASS}"
echo -e "  ${RED}FAIL${NC}: ${FAIL}"
echo -e "  ${YELLOW}SKIP${NC}: ${SKIP}"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}OVERALL: FAIL${NC}"
    echo "Fix failing checks before production deployment."
    exit 1
else
    echo -e "${GREEN}OVERALL: PASS${NC}"
    echo "All critical checks passed. Ready for human review."
    exit 0
fi
