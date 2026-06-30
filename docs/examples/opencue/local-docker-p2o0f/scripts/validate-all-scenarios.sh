#!/usr/bin/env bash
# P2O.0f — Validate All Scenarios
# Runs all 7 scenarios sequentially, collects results, runs safety check.
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PREVIEW_ROOT="${PREVIEW_ROOT:-build/opencue-shared/media-platform-smoke/preview/p2o0f}"
RESULTS_FILE="${PREVIEW_ROOT}/scenario-results.txt"

echo "[P2O.0f] ========================================"
echo "[P2O.0f] Running all scenarios"
echo "[P2O.0f] ========================================"

mkdir -p "${PREVIEW_ROOT}"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

run_scenario() {
    local name="$1"
    local script="$2"

    echo ""
    echo "[P2O.0f] --- ${name} ---"
    if bash "${script}"; then
        echo "[P2O.0f] ${name}: PASS"
        echo "PASS: ${name}" >> "${RESULTS_FILE}"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "[P2O.0f] ${name}: FAIL"
        echo "FAIL: ${name}" >> "${RESULTS_FILE}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

# Initialize results
echo "=== P2O.0f Scenario Results ===" > "${RESULTS_FILE}"
echo "Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "${RESULTS_FILE}"
echo "" >> "${RESULTS_FILE}"

# Run scenarios
run_scenario "Scenario 1: Multi-Frame 10" "${SCRIPT_DIR}/submit-multiframe-10.sh"
run_scenario "Scenario 2: Multi-Frame 20 Chunk=5" "${SCRIPT_DIR}/submit-multiframe-20-chunk5.sh"
run_scenario "Scenario 3: Multi-Layer 2" "${SCRIPT_DIR}/submit-multilayer-2.sh"
run_scenario "Scenario 4: Multi-Layer 3" "${SCRIPT_DIR}/submit-multilayer-3.sh"
run_scenario "Scenario 5: Dependency" "${SCRIPT_DIR}/submit-dependency.sh"
run_scenario "Scenario 6: Failure Exit 1" "${SCRIPT_DIR}/submit-failure-exit1.sh"
run_scenario "Scenario 7: Mixed Failure" "${SCRIPT_DIR}/submit-mixed-failure.sh"

# Copy preview artifacts
echo ""
echo "[P2O.0f] Copying preview artifacts..."
bash "${SCRIPT_DIR}/copy-preview-artifacts.sh" || true

# Safety check
echo ""
echo "[P2O.0f] Running safety check..."
bash "${SCRIPT_DIR}/safety-check.sh" || true

# Summary
echo ""
echo "[P2O.0f] ========================================"
echo "[P2O.0f] RESULTS: ${PASS_COUNT} PASS, ${FAIL_COUNT} FAIL, ${SKIP_COUNT} SKIP"
echo "[P2O.0f] Results: ${RESULTS_FILE}"
echo "[P2O.0f] ========================================"

echo "" >> "${RESULTS_FILE}"
echo "=== Summary ===" >> "${RESULTS_FILE}"
echo "PASS: ${PASS_COUNT}" >> "${RESULTS_FILE}"
echo "FAIL: ${FAIL_COUNT}" >> "${RESULTS_FILE}"
echo "SKIP: ${SKIP_COUNT}" >> "${RESULTS_FILE}"

if [ "${FAIL_COUNT}" -gt 0 ]; then
    exit 1
fi
