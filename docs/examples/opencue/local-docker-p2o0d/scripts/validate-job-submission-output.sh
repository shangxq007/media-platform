#!/usr/bin/env bash
# P2O.0d — Validate job submission output artifacts
set -euo pipefail

PREVIEW_ROOT="${1:-build/opencue-shared/media-platform-smoke/preview/p2o0d}"

echo "[P2O.0d] Validating preview artifacts: ${PREVIEW_ROOT}"

PASS=0
FAIL=0

check_file() {
    local path="$1"
    local label="$2"
    if [ -f "${path}" ]; then
        echo "  PASS: ${label} (${path})"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: ${label} (${path} not found)"
        FAIL=$((FAIL + 1))
    fi
}

check_ffprobe() {
    local path="$1"
    local label="$2"
    if [ -f "${path}" ]; then
        if ffprobe -v error "${path}" >/dev/null 2>&1; then
            echo "  PASS: ${label} (ffprobe readable)"
            PASS=$((PASS + 1))
        else
            echo "  FAIL: ${label} (ffprobe cannot read)"
            FAIL=$((FAIL + 1))
        fi
    else
        echo "  FAIL: ${label} (not found)"
        FAIL=$((FAIL + 1))
    fi
}

echo "--- Smoke Level 0 ---"
check_file "${PREVIEW_ROOT}/smoke-level-0/shared-path-probe.txt" "shared-path-probe.txt"

echo "--- Smoke Level 1 ---"
check_ffprobe "${PREVIEW_ROOT}/smoke-level-1/output.mp4" "output.mp4"
check_file "${PREVIEW_ROOT}/smoke-level-1/ffprobe-output.txt" "ffprobe-output.txt"

echo "--- Smoke Level 2 ---"
check_ffprobe "${PREVIEW_ROOT}/smoke-level-2/input-fixture.mp4" "input-fixture.mp4"
check_ffprobe "${PREVIEW_ROOT}/smoke-level-2/output.mp4" "output.mp4"
check_file "${PREVIEW_ROOT}/smoke-level-2/caption-overlay-input.ass" "caption-overlay-input.ass"
check_file "${PREVIEW_ROOT}/smoke-level-2/local-render-execution-report.txt" "local-render-execution-report.txt"
check_file "${PREVIEW_ROOT}/smoke-level-2/ffprobe-input.txt" "ffprobe-input.txt"
check_file "${PREVIEW_ROOT}/smoke-level-2/ffprobe-output.txt" "ffprobe-output.txt"

echo ""
echo "=== Summary ==="
echo "  Passed: ${PASS}"
echo "  Failed: ${FAIL}"
if [ "${FAIL}" -eq 0 ]; then
    echo "  Result: PASS"
else
    echo "  Result: FAIL"
    exit 1
fi
