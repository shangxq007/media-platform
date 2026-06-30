#!/usr/bin/env bash
# P2O.0e — Validate gRPC submission output artifacts
# Checks that expected output files exist in preview directories.
# Operator-run only. Not production.
set -euo pipefail

PREVIEW_ROOT="${1:-build/opencue-shared/media-platform-smoke/preview/p2o0e}"

echo "[P2O.0e] Validating preview artifacts: ${PREVIEW_ROOT}"

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

echo "--- Smoke Level 0: Shared Path Probe ---"
check_file "${PREVIEW_ROOT}/smoke-level-0/shared-path-probe.txt" "shared-path-probe.txt"
check_file "${PREVIEW_ROOT}/smoke-level-0/opencue-job-summary.txt" "opencue-job-summary.txt"

echo "--- Smoke Level 1: FFmpeg Probe ---"
check_ffprobe "${PREVIEW_ROOT}/smoke-level-1/output.mp4" "output.mp4"
check_file "${PREVIEW_ROOT}/smoke-level-1/ffprobe-output.txt" "ffprobe-output.txt"
check_file "${PREVIEW_ROOT}/smoke-level-1/ffmpeg.stderr.log" "ffmpeg.stderr.log"
check_file "${PREVIEW_ROOT}/smoke-level-1/opencue-job-summary.txt" "opencue-job-summary.txt"

echo "--- Smoke Level 2: Caption Overlay ---"
check_ffprobe "${PREVIEW_ROOT}/smoke-level-2/output.mp4" "output.mp4"
check_file "${PREVIEW_ROOT}/smoke-level-2/ffprobe-output.txt" "ffprobe-output.txt"
check_file "${PREVIEW_ROOT}/smoke-level-2/ffmpeg.stderr.log" "ffmpeg.stderr.log"
check_file "${PREVIEW_ROOT}/smoke-level-2/opencue-job-summary.txt" "opencue-job-summary.txt"

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
