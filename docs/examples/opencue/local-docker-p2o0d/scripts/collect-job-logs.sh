#!/usr/bin/env bash
# P2O.0d — Collect OpenCue job logs from containers
set -euo pipefail

LOG_DIR="${1:-/tmp/p2o0d-job-logs}"

echo "[P2O.0d] Collecting job logs to: ${LOG_DIR}"
mkdir -p "${LOG_DIR}"

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"

# Collect container logs
docker compose -f "${COMPOSE_FILE}" logs cuebot > "${LOG_DIR}/cuebot.log" 2>&1 || echo "  WARN: cuebot log failed"
docker compose -f "${COMPOSE_FILE}" logs rqd > "${LOG_DIR}/rqd.log" 2>&1 || echo "  WARN: rqd log failed"
docker compose -f "${COMPOSE_FILE}" logs postgres > "${LOG_DIR}/postgres.log" 2>&1 || echo "  WARN: postgres log failed"

# Copy shared path logs
SHARED_ROOT="build/opencue-shared/media-platform-smoke"
cp -r "${SHARED_ROOT}/jobs/smoke-001/logs/" "${LOG_DIR}/shared-logs/" 2>/dev/null || echo "  WARN: shared logs not found"

echo "[P2O.0d] Logs collected:"
ls -la "${LOG_DIR}/"
echo "[P2O.0d] Done."
