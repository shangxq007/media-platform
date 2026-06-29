#!/usr/bin/env bash
# P2O.0a — Validate Smoke Output
# Validates file existence and runs ffprobe if available.
# Operator-run manual testbed command only. Not production runtime.
# Usage: bash validate-smoke-output.sh [SHARED_ROOT]
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
PASS=0
FAIL=0

echo "[P2O.0a] Validating smoke output under: ${JOB_DIR}"

check_file() {
  local path="$1"
  local label="$2"
  if [ -f "${path}" ]; then
    echo "  ✅ ${label}: ${path} ($(wc -c < "${path}") bytes)"
    PASS=$((PASS + 1))
  else
    echo "  ❌ ${label}: ${path} MISSING"
    FAIL=$((FAIL + 1))
  fi
}

check_dir() {
  local path="$1"
  local label="$2"
  if [ -d "${path}" ]; then
    echo "  ✅ ${label}: ${path}"
  else
    echo "  ❌ ${label}: ${path} MISSING"
    FAIL=$((FAIL + 1))
  fi
}

echo ""
echo "=== Directory Structure ==="
check_dir "${JOB_DIR}/input" "input/"
check_dir "${JOB_DIR}/work" "work/"
check_dir "${JOB_DIR}/output" "output/"
check_dir "${JOB_DIR}/logs" "logs/"

echo ""
echo "=== Smoke Level 0 Output ==="
check_file "${JOB_DIR}/logs/shared-path-probe.txt" "shared-path-probe"

echo ""
echo "=== Smoke Level 1 Output ==="
check_file "${JOB_DIR}/output/output.mp4" "output.mp4"
check_file "${JOB_DIR}/logs/ffprobe-output.txt" "ffprobe-output"

echo ""
echo "=== Smoke Level 2 Output ==="
check_file "${JOB_DIR}/input/input-fixture.mp4" "input-fixture.mp4"
check_file "${JOB_DIR}/work/caption-overlay-input.ass" "caption-overlay-input.ass"
check_file "${JOB_DIR}/output/output.mp4" "output.mp4 (level 2)"
check_file "${JOB_DIR}/output/local-render-execution-report.txt" "execution-report"
check_file "${JOB_DIR}/logs/ffprobe-input.txt" "ffprobe-input"
check_file "${JOB_DIR}/logs/ffprobe-output.txt" "ffprobe-output (level 2)"

echo ""
echo "=== ffprobe validation ==="
if command -v ffprobe &>/dev/null; then
  if [ -f "${JOB_DIR}/output/output.mp4" ]; then
    echo "  ffprobe output.mp4:"
    ffprobe -v error -show_entries stream=codec_name,width,height -show_entries format=format_name,duration,size -of default=noprint_wrappers=1 "${JOB_DIR}/output/output.mp4" 2>&1 | sed 's/^/    /'
    echo "  ✅ ffprobe validation complete"
  else
    echo "  ⚠️  output.mp4 not found, skipping ffprobe"
  fi
else
  echo "  ⚠️  ffprobe not available on host"
fi

echo ""
echo "=== Summary ==="
echo "  Passed: ${PASS}"
echo "  Failed: ${FAIL}"
if [ "${FAIL}" -gt 0 ]; then
  echo "  Result: FAIL"
  exit 1
else
  echo "  Result: PASS"
fi
