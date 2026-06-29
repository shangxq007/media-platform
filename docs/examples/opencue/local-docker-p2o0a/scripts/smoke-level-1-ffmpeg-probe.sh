#!/usr/bin/env bash
# P2O.0a — Smoke Level 1: FFmpeg Probe
# Proves worker can run ffmpeg and write MP4, then validate with ffprobe.
# Operator-run manual testbed command only. Not production runtime.
# Usage: bash smoke-level-1-ffmpeg-probe.sh [SHARED_ROOT]
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
FFMPEG_STDOUT="${JOB_DIR}/logs/ffmpeg.stdout.log"
FFMPEG_STDERR="${JOB_DIR}/logs/ffmpeg.stderr.log"
FFPROBE_LOG="${JOB_DIR}/logs/ffprobe-output.txt"

echo "[P2O.0a] Smoke Level 1 — FFmpeg Probe"
echo "[P2O.0a] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/output" "${JOB_DIR}/logs"

# Step 1: Generate testsrc MP4 (2 seconds, 320x180)
echo "[P2O.0a] Generating testsrc MP4..."
ffmpeg -y \
  -f lavfi -i "testsrc=duration=2:size=320x180:rate=25" \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
  "${OUTPUT_FILE}" \
  > "${FFMPEG_STDOUT}" 2>"${FFMPEG_STDERR}" || {
    echo "[P2O.0a] FFmpeg failed. Check ${FFMPEG_STDERR}"
    exit 1
  }

echo "[P2O.0a] Output: ${OUTPUT_FILE}"
ls -la "${OUTPUT_FILE}"

# Step 2: Validate with ffprobe
echo "[P2O.0a] Validating with ffprobe..."
ffprobe -v error \
  -show_entries stream=codec_name,width,height,duration \
  -show_entries format=format_name,duration,size \
  -of default=noprint_wrappers=1 \
  "${OUTPUT_FILE}" > "${FFPROBE_LOG}" 2>&1 || {
    echo "[P2O.0a] ffprobe failed. Check ${FFPROBE_LOG}"
    exit 1
  }

echo "[P2O.0a] ffprobe output:"
cat "${FFPROBE_LOG}"

echo "[P2O.0a] Smoke Level 1 — PASS"
