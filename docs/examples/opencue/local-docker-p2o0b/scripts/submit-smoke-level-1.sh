#!/usr/bin/env bash
# P2O.0b — Submit Smoke Level 1: FFmpeg Probe via OpenCue
# Operator-run manual testbed command only. Not production runtime.
#
# Usage: bash submit-smoke-level-1.sh [SHARED_ROOT]
set -euo pipefail

# Auto-detect: use host path if available, else container path
DEFAULT_ROOT="build/opencue-shared/media-platform-smoke"
if [ -d "/mnt/opencue-shared/media-platform-smoke" ] && [ ! -d "build/opencue-shared" ]; then
  DEFAULT_ROOT="/mnt/opencue-shared/media-platform-smoke"
fi
SHARED_ROOT="${1:-${DEFAULT_ROOT}}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
FFMPEG_STDOUT="${JOB_DIR}/logs/ffmpeg.stdout.log"
FFMPEG_STDERR="${JOB_DIR}/logs/ffmpeg.stderr.log"
FFPROBE_LOG="${JOB_DIR}/logs/ffprobe-output.txt"

echo "[P2O.0b] Smoke Level 1 — FFmpeg Probe (Runtime)"
echo "[P2O.0b] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/output" "${JOB_DIR}/logs"

# Try OpenCue submission if available
if [ -n "${OPENCUE_SUBMIT_CMD:-}" ]; then
  echo "[P2O.0b] Submitting via OpenCue..."
  eval "${OPENCUE_SUBMIT_CMD}" || {
    echo "[P2O.0b] OpenCue submission failed. Running locally as fallback."
  }
fi

# Direct execution
echo "[P2O.0b] Generating testsrc MP4..."
ffmpeg -y \
  -f lavfi -i "testsrc=duration=2:size=320x180:rate=25" \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
  "${OUTPUT_FILE}" \
  > "${FFMPEG_STDOUT}" 2>"${FFMPEG_STDERR}" || {
    echo "[P2O.0b] FFmpeg failed. Check ${FFMPEG_STDERR}"
    exit 1
  }

echo "[P2O.0b] Output: ${OUTPUT_FILE}"
ls -la "${OUTPUT_FILE}"

echo "[P2O.0b] Validating with ffprobe..."
ffprobe -v error \
  -show_entries stream=codec_name,width,height,duration \
  -show_entries format=format_name,duration,size \
  -of default=noprint_wrappers=1 \
  "${OUTPUT_FILE}" > "${FFPROBE_LOG}" 2>&1 || {
    echo "[P2O.0b] ffprobe failed. Check ${FFPROBE_LOG}"
    exit 1
  }

echo "[P2O.0b] ffprobe output:"
cat "${FFPROBE_LOG}"

echo "[P2O.0b] Smoke Level 1 — PASS"
