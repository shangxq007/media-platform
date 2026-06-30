#!/usr/bin/env bash
# P2O.0f — Poll job status from PostgreSQL
# Usage: poll-job-status.sh <job_name> [max_wait_seconds] [poll_interval]
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
JOB_NAME="${1:?Usage: poll-job-status.sh <job_name> [max_wait] [interval]}"
MAX_WAIT="${2:-180}"
POLL_INTERVAL="${3:-5}"

echo "[P2O.0f] Polling job: ${JOB_NAME} (max ${MAX_WAIT}s, interval ${POLL_INTERVAL}s)"

ELAPSED=0
while [ "${ELAPSED}" -lt "${MAX_WAIT}" ]; do
    JOB_STATUS=$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -t -A -c \
        "SELECT str_state FROM job WHERE str_name='${JOB_NAME}' ORDER BY dt_submitted DESC LIMIT 1;" 2>/dev/null || echo "NOT_FOUND")

    echo "[P2O.0f] [${ELAPSED}s] Job status: ${JOB_STATUS}"

    case "${JOB_STATUS}" in
        SUCCEEDED|FINISHED)
            echo "[P2O.0f] Job ${JOB_STATUS}."
            exit 0
            ;;
        DEAD|SETUP_FAILED|CHECKPOINTED)
            echo "[P2O.0f] Job terminated with status: ${JOB_STATUS}"
            exit 1
            ;;
        NOT_FOUND)
            # Job may not have been created yet
            sleep "${POLL_INTERVAL}"
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
            ;;
        *)
            sleep "${POLL_INTERVAL}"
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
            ;;
    esac
done

echo "[P2O.0f] TIMEOUT: Job did not complete within ${MAX_WAIT}s."
exit 2
