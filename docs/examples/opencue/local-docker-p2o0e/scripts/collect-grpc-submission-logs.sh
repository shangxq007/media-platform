#!/usr/bin/env bash
# P2O.0e — Collect Cuebot and RQD logs for gRPC submission diagnostics
# Collects container logs via docker compose for post-mortem analysis.
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
LOG_DIR="${1:-/tmp/p2o0e-grpc-submission-logs}"

echo "[P2O.0e] Collecting gRPC submission logs to: ${LOG_DIR}"
mkdir -p "${LOG_DIR}"

# Collect Cuebot logs
echo "[P2O.0e] Collecting Cuebot logs..."
docker compose -f "${COMPOSE_FILE}" logs cuebot > "${LOG_DIR}/cuebot.log" 2>&1 || {
    echo "[P2O.0e] WARN: Could not collect Cuebot logs (container may not be running)"
    echo "Cuebot log collection failed" > "${LOG_DIR}/cuebot.log"
}

# Collect RQD logs
echo "[P2O.0e] Collecting RQD logs..."
docker compose -f "${COMPOSE_FILE}" logs rqd > "${LOG_DIR}/rqd.log" 2>&1 || {
    echo "[P2O.0e] WARN: Could not collect RQD logs (container may not be running)"
    echo "RQD log collection failed" > "${LOG_DIR}/rqd.log"
}

# Collect PostgreSQL logs
echo "[P2O.0e] Collecting PostgreSQL logs..."
docker compose -f "${COMPOSE_FILE}" logs postgres > "${LOG_DIR}/postgres.log" 2>&1 || {
    echo "[P2O.0e] WARN: Could not collect PostgreSQL logs"
    echo "PostgreSQL log collection failed" > "${LOG_DIR}/postgres.log"
}

# Dump job table from database for diagnostics
echo "[P2O.0e] Dumping job table from database..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U opencue -d opencue -c \
    "SELECT str_name, str_state, str_show, str_shot, str_user, dt_submitted, dt_started, dt_finished FROM job ORDER BY dt_submitted DESC LIMIT 20;" \
    > "${LOG_DIR}/job-table-dump.txt" 2>/dev/null || {
        echo "[P2O.0e] WARN: Could not dump job table"
        echo "Job table dump failed" > "${LOG_DIR}/job-table-dump.txt"
    }

# Dump host table for diagnostics
echo "[P2O.0e] Dumping host table from database..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U opencue -d opencue -c \
    "SELECT str_name, str_facility, str_lock_state, int_cores, int_memory FROM host;" \
    > "${LOG_DIR}/host-table-dump.txt" 2>/dev/null || {
        echo "[P2O.0e] WARN: Could not dump host table"
        echo "Host table dump failed" > "${LOG_DIR}/host-table-dump.txt"
    }

echo ""
echo "[P2O.0e] Logs collected:"
ls -la "${LOG_DIR}/"
echo "[P2O.0e] Done."
