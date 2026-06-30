#!/usr/bin/env bash
# P2O.0d — Submit Smoke Level 1: FFmpeg Probe via RQD Container Exec
# Fallback: runs inside RQD container, not true OpenCue job submission.
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
FFMPEG_STDOUT="${JOB_DIR}/logs/ffmpeg.stdout.log"
FFMPEG_STDERR="${JOB_DIR}/logs/ffmpeg.stderr.log"
FFPROBE_LOG="${JOB_DIR}/logs/ffprobe-output.txt"
JOB_SUMMARY="${JOB_DIR}/logs/opencue-job-summary-level1.txt"
WORKER_LOG="${JOB_DIR}/logs/worker-log-level1.txt"

echo "[P2O.0d] Smoke Level 1 — FFmpeg Probe (Container Exec)"
echo "[P2O.0d] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/output" "${JOB_DIR}/logs"

# Write job summary
cat > "${JOB_SUMMARY}" << 'EOF'
=== OpenCue Job Summary (P2O.0d Level 1) ===
Submission Method: container-exec (fallback)
Container: opencue-rqd
Command: smoke-level-1-ffmpeg-probe
Status: SUBMITTED_VIA_CONTAINER_EXEC
Note: Not true OpenCue job submission. Fallback only.
EOF

# Execute inside RQD container
echo "[P2O.0d] Executing FFmpeg probe in RQD container..."
docker exec opencue-rqd sh -c '
  SHARED_ROOT="/mnt/opencue-shared/media-platform-smoke"
  JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
  OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
  FFMPEG_STDOUT="${JOB_DIR}/logs/ffmpeg.stdout.log"
  FFMPEG_STDERR="${JOB_DIR}/logs/ffmpeg.stderr.log"
  FFPROBE_LOG="${JOB_DIR}/logs/ffprobe-output.txt"

  mkdir -p "${JOB_DIR}/output" "${JOB_DIR}/logs"

  # Step 1: Generate testsrc MP4
  echo "[RQD] Generating testsrc MP4..."
  ffmpeg -y \
    -f lavfi -i "testsrc=duration=2:size=320x180:rate=25" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
    "${OUTPUT_FILE}" \
    > "${FFMPEG_STDOUT}" 2>"${FFMPEG_STDERR}" || {
      echo "[RQD] FFmpeg failed. Check ${FFMPEG_STDERR}"
      exit 1
    }

  echo "[RQD] Output: ${OUTPUT_FILE}"
  ls -la "${OUTPUT_FILE}"

  # Step 2: Validate with ffprobe
  echo "[RQD] Validating with ffprobe..."
  ffprobe -v error \
    -show_entries stream=codec_name,width,height,duration \
    -show_entries format=format_name,duration,size \
    -of default=noprint_wrappers=1 \
    "${OUTPUT_FILE}" > "${FFPROBE_LOG}" 2>&1 || {
      echo "[RQD] ffprobe failed. Check ${FFPROBE_LOG}"
      exit 1
    }

  echo "[RQD] ffprobe output:"
  cat "${FFPROBE_LOG}"
  echo "[RQD] Smoke Level 1 — PASS"
' > "${WORKER_LOG}" 2>&1 || {
    echo "[P2O.0d] Container exec failed. Check ${WORKER_LOG}"
    exit 1
}

# Verify output exists on host
if [ -f "${OUTPUT_FILE}" ]; then
    echo "[P2O.0d] Output: ${OUTPUT_FILE}"
    ls -la "${OUTPUT_FILE}"
    echo "[P2O.0d] Smoke Level 1 — PASS"
else
    echo "[P2O.0d] ERROR: Output file not found on host: ${OUTPUT_FILE}"
    exit 1
fi
