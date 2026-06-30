#!/usr/bin/env bash
# P2O.0c — Collect runtime logs
# Operator-run only. Not production.
set -euo pipefail

LOG_DIR="${1:-/tmp/p2o0c-runtime-logs}"

echo "[P2O.0c] Collecting runtime logs to: ${LOG_DIR}"
mkdir -p "${LOG_DIR}"

docker logs opencue-postgres > "${LOG_DIR}/postgres.log" 2>&1 || true
docker logs opencue-cuebot > "${LOG_DIR}/cuebot.log" 2>&1 || true
docker logs opencue-rqd > "${LOG_DIR}/rqd.log" 2>&1 || true

echo "[P2O.0c] Logs collected:"
ls -la "${LOG_DIR}/"
echo "[P2O.0c] Done."
