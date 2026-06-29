#!/usr/bin/env bash
# P2O.0b — Collect OpenCue Logs
# Collects Cuebot and RQD logs from Docker containers.
# Operator-run manual testbed command only. Not production runtime.
# Usage: bash collect-opencue-logs.sh [SHARED_ROOT]
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
LOG_DIR="${SHARED_ROOT}/jobs/smoke-001/logs"

echo "[P2O.0b] Collecting OpenCue logs to: ${LOG_DIR}"
mkdir -p "${LOG_DIR}"

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml"

# Collect Cuebot logs
echo "[P2O.0b] Collecting Cuebot logs..."
docker compose -f "${COMPOSE_FILE}" logs cuebot > "${LOG_DIR}/cuebot.log" 2>&1 || {
  echo "[P2O.0b] Could not collect Cuebot logs (container may not be running)"
  echo "Cuebot log collection failed" > "${LOG_DIR}/cuebot.log"
}

# Collect RQD logs
echo "[P2O.0b] Collecting RQD logs..."
docker compose -f "${COMPOSE_FILE}" logs rqd > "${LOG_DIR}/rqd.log" 2>&1 || {
  echo "[P2O.0b] Could not collect RQD logs (container may not be running)"
  echo "RQD log collection failed" > "${LOG_DIR}/rqd.log"
}

# Collect PostgreSQL logs
echo "[P2O.0b] Collecting PostgreSQL logs..."
docker compose -f "${COMPOSE_FILE}" logs postgres > "${LOG_DIR}/postgres.log" 2>&1 || {
  echo "[P2O.0b] Could not collect PostgreSQL logs"
  echo "PostgreSQL log collection failed" > "${LOG_DIR}/postgres.log"
}

echo "[P2O.0b] Logs collected:"
ls -la "${LOG_DIR}"/*.log 2>/dev/null || echo "  No log files found"
echo "[P2O.0b] Done."
