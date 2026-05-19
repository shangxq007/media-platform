#!/usr/bin/env bash
# scripts/infra-validate.sh
# Validates infrastructure configuration without applying anything.
#
# Usage: bash scripts/infra-validate.sh
#
# This script does NOT run terraform/tofu apply/destroy.
# It only validates configuration files.

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

PASS=0
FAIL=0

check_pass() {
    log_info "  PASS: $1"
    PASS=$((PASS + 1))
}

check_fail() {
    log_error "  FAIL: $1"
    FAIL=$((FAIL + 1))
}

# ─── 1. Docker Compose validation ──────────────────────────────────────────
log_info "Step 1: Validating docker compose config..."
if docker compose config > /dev/null 2>&1; then
    check_pass "docker compose config is valid"
else
    check_fail "docker compose config is invalid"
fi

# ─── 2. Dockerfile validation ──────────────────────────────────────────────
log_info "Step 2: Checking Dockerfile..."
if [ -f Dockerfile ]; then
    check_pass "Dockerfile exists"
    if grep -q "FROM" Dockerfile; then
        check_pass "Dockerfile has FROM instruction"
    else
        check_fail "Dockerfile missing FROM instruction"
    fi
else
    check_fail "Dockerfile not found"
fi

# ─── 3. .env.example validation ────────────────────────────────────────────
log_info "Step 3: Checking .env.example..."
if [ -f .env.example ]; then
    check_pass ".env.example exists"
    # Check no real secrets
    if grep -q "change-me\|EXAMPLE_\|your-" .env.example; then
        check_pass ".env.example uses placeholder values"
    else
        log_warn ".env.example may contain real values — verify manually"
    fi
else
    check_fail ".env.example not found"
fi

# ─── 4. Gitignore checks ───────────────────────────────────────────────────
log_info "Step 4: Checking .gitignore..."
for f in .env .env.local; do
    if git check-ignore -q "$f" 2>/dev/null; then
        check_pass "${f} is gitignored"
    else
        check_fail "${f} is NOT gitignored"
    fi
done

# ─── 5. Terraform / OpenTofu validation (if present) ───────────────────────
log_info "Step 5: Checking infrastructure-as-code..."
INFRA_DIR="${PROJECT_ROOT}/infra"
if [ -d "$INFRA_DIR" ]; then
    check_pass "infra/ directory exists"

    TF_COUNT=$(find "$INFRA_DIR" -name "*.tf" -o -name "*.tfvars" 2>/dev/null | wc -l)
    if [ "$TF_COUNT" -gt 0 ]; then
        check_pass "Found ${TF_COUNT} terraform/tofu files"

        # Check for destructive defaults
        if grep -r "prevent_destroy" "$INFRA_DIR"/*.tf 2>/dev/null | grep -q "true"; then
            check_pass "prevent_destroy is enabled"
        else
            log_warn "Consider adding prevent_destroy to critical resources"
        fi
    else
        log_warn "No .tf files found in infra/"
    fi
else
    log_warn "infra/ directory not found — skipping IaC checks"
fi

# ─── 6. Scripts validation ─────────────────────────────────────────────────
log_info "Step 6: Checking scripts..."
for script in scripts/local-docker-test.sh scripts/infra-validate.sh; do
    if [ -f "$script" ]; then
        if bash -n "$script" 2>/dev/null; then
            check_pass "${script} syntax OK"
        else
            check_fail "${script} has syntax errors"
        fi
    else
        check_fail "${script} not found"
    fi
done

# ─── Summary ──────────────────────────────────────────────────────────────
echo ""
log_info "Validation results: ${PASS} passed, ${FAIL} failed"
if [ $FAIL -gt 0 ]; then
    log_error "Some checks failed!"
    exit 1
fi
log_info "All checks passed!"
