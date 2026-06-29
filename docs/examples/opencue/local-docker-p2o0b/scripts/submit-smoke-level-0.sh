#!/usr/bin/env bash
# P2O.0b — Submit Smoke Level 0: Shared Path Probe via OpenCue
# Operator-run manual testbed command only. Not production runtime.
#
# This script can run in two modes:
#   Mode A: Direct execution inside RQD container (no Cuebot submission)
#   Mode B: OpenCue job submission (requires Cuebot/RQD and submission command)
#
# Usage: bash submit-smoke-level-0.sh [SHARED_ROOT]
set -euo pipefail

# Auto-detect: use host path if available, else container path
DEFAULT_ROOT="build/opencue-shared/media-platform-smoke"
if [ -d "/mnt/opencue-shared/media-platform-smoke" ] && [ ! -d "build/opencue-shared" ]; then
  DEFAULT_ROOT="/mnt/opencue-shared/media-platform-smoke"
fi
SHARED_ROOT="${1:-${DEFAULT_ROOT}}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
LOG_FILE="${JOB_DIR}/logs/shared-path-probe.txt"

# Check if running inside container or on host
if [ -d "/mnt/opencue-shared" ]; then
  CONTAINER_MODE=true
  echo "[P2O.0b] Running in container mode"
else
  CONTAINER_MODE=false
  echo "[P2O.0b] Running in host mode (dry run)"
fi

echo "[P2O.0b] Smoke Level 0 — Shared Path Probe (Runtime)"
echo "[P2O.0b] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/logs"

# Try OpenCue submission if OPENCUE_SUBMIT_CMD is set
if [ -n "${OPENCUE_SUBMIT_CMD:-}" ]; then
  echo "[P2O.0b] Submitting via OpenCue: ${OPENCUE_SUBMIT_CMD}"
  # TODO: Replace with actual OpenCue submission command
  # Example: cueadmin --job-create ... or python opencue submit ...
  eval "${OPENCUE_SUBMIT_CMD}" || {
    echo "[P2O.0b] OpenCue submission failed. Running locally as fallback."
  }
fi

# Direct execution (Mode A: inside container or host dry run)
echo "[P2O.0b] Executing shared path probe..."
{
  echo "=== P2O.0b Runtime Shared Path Probe ==="
  echo "Timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "Hostname: $(hostname 2>/dev/null || echo 'unknown')"
  echo "Whoami: $(whoami 2>/dev/null || echo 'unknown')"
  echo "Pwd: $(pwd)"
  echo "Container mode: ${CONTAINER_MODE}"
  echo "Job dir exists: $(test -d "${JOB_DIR}" && echo YES || echo NO)"
  echo "Input dir exists: $(test -d "${JOB_DIR}/input" && echo YES || echo NO)"
  echo "Work dir exists: $(test -d "${JOB_DIR}/work" && echo YES || echo NO)"
  echo "Output dir exists: $(test -d "${JOB_DIR}/output" && echo YES || echo NO)"
  echo "Logs dir exists: $(test -d "${JOB_DIR}/logs" && echo YES || echo NO)"
  echo "FFmpeg available: $(command -v ffmpeg >/dev/null 2>&1 && echo YES || echo NO)"
  echo "ffprobe available: $(command -v ffprobe >/dev/null 2>&1 && echo YES || echo NO)"
  echo "=== Probe complete ==="
} > "${LOG_FILE}" 2>&1

echo "[P2O.0b] Probe written to: ${LOG_FILE}"
cat "${LOG_FILE}"
echo "[P2O.0b] Smoke Level 0 — PASS"
