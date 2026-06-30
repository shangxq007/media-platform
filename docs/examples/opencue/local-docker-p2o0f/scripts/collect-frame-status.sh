#!/usr/bin/env bash
# P2O.0f — Collect frame-level status from PostgreSQL
# Usage: collect-frame-status.sh <job_name> <output_file>
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
JOB_NAME="${1:?Usage: collect-frame-status.sh <job_name> <output_file>}"
OUTPUT_FILE="${2:?Usage: collect-frame-status.sh <job_name> <output_file>}"

echo "[P2O.0f] Collecting frame status for: ${JOB_NAME}"

mkdir -p "$(dirname "${OUTPUT_FILE}")"

# Get job PK (use LIKE pattern since Cuebot transforms job names)
JOB_NAME_PATTERN=$(echo "${JOB_NAME}" | sed 's/^p2o0f-//' | sed 's/-/_/g')
JOB_PK=$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U opencue -d opencue -t -A -c \
    "SELECT pk_job FROM job WHERE str_name LIKE '%${JOB_NAME_PATTERN}%' ORDER BY ts_started DESC LIMIT 1;" 2>/dev/null || echo "")

if [ -z "${JOB_PK}" ]; then
    echo "[P2O.0f] WARN: Job not found: ${JOB_NAME}"
    echo "JOB_NOT_FOUND" > "${OUTPUT_FILE}"
    exit 1
fi

{
    echo "=== Job: ${JOB_NAME} (pk=${JOB_PK}) ==="
    echo ""
    echo "--- Job Status ---"
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -c \
        "SELECT str_name, str_state, str_show, str_shot, str_user, ts_started, ts_stopped FROM job WHERE pk_job='${JOB_PK}';" 2>/dev/null
    echo ""
    echo "--- Layer Status ---"
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -c \
        "SELECT str_name, str_type, int_cores_min, int_mem_min FROM layer WHERE pk_job='${JOB_PK}' ORDER BY str_name;" 2>/dev/null
    echo ""
    echo "--- Frame Status ---"
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -c \
        "SELECT f.int_dispatch_order, l.str_name as layer, f.str_state, f.int_exit_status, f.ts_started, f.ts_stopped FROM frame f JOIN layer l ON f.pk_layer = l.pk_layer WHERE f.pk_job='${JOB_PK}' ORDER BY l.str_name, f.int_dispatch_order;" 2>/dev/null
    echo ""
    echo "--- Frame State Counts ---"
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -c \
        "SELECT str_state, count(*) FROM frame WHERE pk_job='${JOB_PK}' GROUP BY str_state ORDER BY count DESC;" 2>/dev/null
} > "${OUTPUT_FILE}"

echo "[P2O.0f] Frame status written to: ${OUTPUT_FILE}"
