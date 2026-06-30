#!/usr/bin/env bash
# P2O.0c — Check RQD shared path mount
# Operator-run only. Not production.
set -euo pipefail

SHARED_ROOT_CONTAINER="/mnt/opencue-shared/media-platform-smoke"
LOG_FILE="${SHARED_ROOT_CONTAINER}/jobs/smoke-001/logs/rqd-shared-path-ready.txt"

echo "[P2O.0c] Checking RQD shared path mount..."

# Check if shared path exists in RQD container
if docker exec opencue-rqd test -d "${SHARED_ROOT_CONTAINER}"; then
    echo "[P2O.0c] Shared path exists in RQD: ${SHARED_ROOT_CONTAINER}"
else
    echo "[P2O.0c] ERROR: Shared path NOT found in RQD: ${SHARED_ROOT_CONTAINER}"
    exit 1
fi

# Check if RQD can write to shared path
docker exec opencue-rqd sh -c "mkdir -p ${SHARED_ROOT_CONTAINER}/jobs/smoke-001/logs && echo 'rqd-shared-path-ready at $(date -u +%Y-%m-%dT%H:%M:%SZ)' > ${LOG_FILE}" 2>&1

if docker exec opencue-rqd test -f "${LOG_FILE}"; then
    echo "[P2O.0c] RQD can write to shared path. Content:"
    docker exec opencue-rqd cat "${LOG_FILE}"
else
    echo "[P2O.0c] ERROR: RQD cannot write to shared path."
    exit 1
fi

echo "[P2O.0c] Done."
