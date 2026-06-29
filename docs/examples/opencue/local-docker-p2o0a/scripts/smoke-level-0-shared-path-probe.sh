#!/usr/bin/env bash
# P2O.0a — Smoke Level 0: Shared Path Probe
# Proves worker container can access shared path.
# Operator-run manual testbed command only. Not production runtime.
# Usage: bash smoke-level-0-shared-path-probe.sh [SHARED_ROOT]
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
LOG_FILE="${JOB_DIR}/logs/shared-path-probe.txt"

echo "[P2O.0a] Smoke Level 0 — Shared Path Probe"
echo "[P2O.0a] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/logs"

{
  echo "=== P2O.0a Shared Path Probe ==="
  echo "Timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "Hostname: $(hostname 2>/dev/null || echo 'unknown')"
  echo "Whoami: $(whoami 2>/dev/null || echo 'unknown')"
  echo "Pwd: $(pwd)"
  echo "Job dir exists: $(test -d "${JOB_DIR}" && echo YES || echo NO)"
  echo "Input dir exists: $(test -d "${JOB_DIR}/input" && echo YES || echo NO)"
  echo "Work dir exists: $(test -d "${JOB_DIR}/work" && echo YES || echo NO)"
  echo "Output dir exists: $(test -d "${JOB_DIR}/output" && echo YES || echo NO)"
  echo "Logs dir exists: $(test -d "${JOB_DIR}/logs" && echo YES || echo NO)"
  echo "=== Probe complete ==="
} > "${LOG_FILE}" 2>&1

echo "[P2O.0a] Probe written to: ${LOG_FILE}"
cat "${LOG_FILE}"
echo "[P2O.0a] Smoke Level 0 — PASS"
