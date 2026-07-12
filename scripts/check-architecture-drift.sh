#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0
CHECKS=0

pass() { printf "✅ PASS: %s\n" "$1"; CHECKS=$((CHECKS + 1)); }
fail() { printf "❌ FAIL: %s\n" "$1" >&2; FAILED=1; CHECKS=$((CHECKS + 1)); }

echo "=== Architecture Drift Guard ==="
echo ""

# --- Required Classes ---
echo "--- Required Classes ---"

for class in StorageDeliveryProfileRegistry StorageDeliveryProfileCatalog StorageDeliveryProfileRegistrySnapshot \
             StorageDeliveryProfileValidator StorageDeliveryProfileConfigProperties \
             ReportOnlyPreflightPolicyEvaluator PreflightPolicyEvaluationResult \
             SafePreflightReportSummary UploadReportOnlyPreflightHook; do
    if find . -path './build' -prune -o -path './.git' -prune -o -name "${class}.java" -print | grep -q .; then
        pass "Required class exists: $class"
    else
        fail "Required class missing: $class"
    fi
done

echo ""
echo "--- Runtime Profile Switching ---"

if grep -R "StorageDeliveryProfileResolver" . --include='*.java' 2>/dev/null | grep -v '/src/test/' | grep -v '/docs/' | grep -q .; then
    fail "StorageDeliveryProfileResolver appeared in production code"
else
    pass "No StorageDeliveryProfileResolver in production code"
fi

echo ""
echo "--- Storage Exposure ---"

for pattern in "accessKey" "secretKey" "credentials"; do
    if grep -R "private.*$pattern\|String $pattern\|boolean $pattern" . --include='*.java' -l 2>/dev/null | grep -i "StorageDelivery" | grep -v '/src/test/' | grep -q .; then
        fail "StorageDelivery classes contain credential field: $pattern"
    else
        pass "StorageDelivery classes don't contain credential field: $pattern"
    fi
done

echo ""
echo "--- Report-only Evaluator ---"

EVALUATOR_FILE="$(find . -name 'ReportOnlyPreflightPolicyEvaluator.java' | head -n 1)"
if [ -n "$EVALUATOR_FILE" ]; then
    if grep -q "return PreflightPolicyDecision.REJECT;" "$EVALUATOR_FILE" 2>/dev/null; then
        fail "Report-only evaluator must not emit REJECT"
    else
        pass "Report-only evaluator does not emit REJECT"
    fi
else
    fail "ReportOnlyPreflightPolicyEvaluator.java not found"
fi

echo ""
echo "--- Upload Rejection / Enforce ---"

if grep -R "PreflightRejected" . --include='*.java' 2>/dev/null | grep -v '/src/test/' | grep -v '/docs/' | grep -q .; then
    fail "PreflightRejected appeared in production code"
else
    pass "No PreflightRejected in production code"
fi

echo ""
echo "--- Persistence ---"

for pattern in "PreflightPolicyEvaluationRepository" "PreflightReportRepository" "SafePreflightReportRepository"; do
    if find . -path './build' -prune -o -name "${pattern}.java" -print | grep -q .; then
        fail "Persistence repository found: $pattern"
    else
        pass "No persistence repository: $pattern"
    fi
done

echo ""
echo "--- Deferred Status ---"

if grep -qi "opencue" AGENTS.md 2>/dev/null; then
    pass "OpenCue reference exists in AGENTS.md"
else
    fail "OpenCue not found in AGENTS.md"
fi

if grep -qi "artifact.dag.*postponed\|artifact.dag.*deferred" AGENTS.md 2>/dev/null; then
    pass "Artifact DAG remains POSTPONED/DEFERRED"
else
    fail "Artifact DAG status not found as POSTPONED/DEFERRED"
fi

echo ""
echo "=== Summary ==="
echo "Checks: $CHECKS"
echo "Failed: $FAILED"

if [ $FAILED -eq 0 ]; then
    echo "✅ All architecture drift checks passed"
    exit 0
else
    echo "❌ Architecture drift detected"
    exit 1
fi
