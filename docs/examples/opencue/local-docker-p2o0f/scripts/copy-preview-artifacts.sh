#!/usr/bin/env bash
# P2O.0f — Copy preview artifacts from Docker volume to host
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
PREVIEW_ROOT="${PREVIEW_ROOT:-build/opencue-shared/media-platform-smoke/preview/p2o0f}"

echo "[P2O.0f] Copying preview artifacts from Docker volume..."

# The shared path is mounted as a Docker volume.
# Files written by RQD inside the container are accessible via docker exec.
# We copy them to the host preview directory.

SCENARIOS="multiframe-10 multiframe-20-chunk5 multilayer-2 multilayer-3 dependency failure-exit1 mixed-failure"

for SCENARIO in ${SCENARIOS}; do
    DEST="${PREVIEW_ROOT}/${SCENARIO}"
    SRC="/mnt/opencue-shared/media-platform-smoke/preview/p2o0f/${SCENARIO}"
    mkdir -p "${DEST}"

    echo "[P2O.0f] Copying: ${SCENARIO}"

    # List files in container
    FILES=$(docker compose -f "${COMPOSE_FILE}" exec -T rqd \
        find "${SRC}" -type f 2>/dev/null || echo "")

    if [ -z "${FILES}" ]; then
        echo "[P2O.0f] WARN: No files found for ${SCENARIO}"
        continue
    fi

    # Copy each file
    for FILE in ${FILES}; do
        FILENAME=$(basename "${FILE}")
        docker compose -f "${COMPOSE_FILE}" exec -T rqd \
            cat "${FILE}" > "${DEST}/${FILENAME}" 2>/dev/null || \
            echo "[P2O.0f] WARN: Could not copy ${FILE}"
    done

    echo "[P2O.0f] Copied $(ls "${DEST}" | wc -l) files for ${SCENARIO}"
done

echo "[P2O.0f] Preview artifacts copied to: ${PREVIEW_ROOT}"
