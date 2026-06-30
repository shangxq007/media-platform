#!/usr/bin/env bash
# P2O.0e — Copy preview artifacts from Docker volume to host
# Uses docker cp to retrieve output files from the media-smoke volume
# mounted at /mnt/opencue-shared/ inside the containers.
# Operator-run only. Not production.
set -euo pipefail

PREVIEW_ROOT="${1:-build/opencue-shared/media-platform-smoke/preview/p2o0e}"

echo "[P2O.0e] Copying preview artifacts from Docker volume to host..."
echo "[P2O.0e] Host preview root: ${PREVIEW_ROOT}"

mkdir -p "${PREVIEW_ROOT}/smoke-level-0"
mkdir -p "${PREVIEW_ROOT}/smoke-level-1"
mkdir -p "${PREVIEW_ROOT}/smoke-level-2"

CONTAINER_VOLUME_ROOT="/mnt/opencue-shared/media-platform-smoke/preview/p2o0e"

# Smoke Level 0
echo "  Copying smoke-level-0..."
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-0/shared-path-probe.txt" \
    "${PREVIEW_ROOT}/smoke-level-0/" 2>/dev/null || echo "  WARN: shared-path-probe.txt not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-0/opencue-job-summary.txt" \
    "${PREVIEW_ROOT}/smoke-level-0/" 2>/dev/null || echo "  WARN: opencue-job-summary.txt not found in container"

# Smoke Level 1
echo "  Copying smoke-level-1..."
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-1/output.mp4" \
    "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: output.mp4 not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-1/ffmpeg.stderr.log" \
    "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: ffmpeg.stderr.log not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-1/ffprobe-output.txt" \
    "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: ffprobe-output.txt not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-1/opencue-job-summary.txt" \
    "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: opencue-job-summary.txt not found in container"

# Smoke Level 2
echo "  Copying smoke-level-2..."
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-2/output.mp4" \
    "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: output.mp4 not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-2/ffmpeg.stderr.log" \
    "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: ffmpeg.stderr.log not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-2/ffprobe-output.txt" \
    "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: ffprobe-output.txt not found in container"
docker cp "opencue-rqd:${CONTAINER_VOLUME_ROOT}/smoke-level-2/opencue-job-summary.txt" \
    "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: opencue-job-summary.txt not found in container"

echo ""
echo "[P2O.0e] Preview artifacts copied to: ${PREVIEW_ROOT}"
find "${PREVIEW_ROOT}" -type f | sort
echo "[P2O.0e] Done."
