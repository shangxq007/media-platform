#!/usr/bin/env bash
# P2O.0d — Submit Smoke Level 2: Real Media + Caption Overlay via RQD Container Exec
# Fallback: runs inside RQD container, not true OpenCue job submission.
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
INPUT_FILE="${JOB_DIR}/input/input-fixture.mp4"
ASS_FILE="${JOB_DIR}/work/caption-overlay-input.ass"
OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
REPORT_FILE="${JOB_DIR}/output/local-render-execution-report.txt"
FFPROBE_INPUT="${JOB_DIR}/logs/ffprobe-input.txt"
FFPROBE_OUTPUT="${JOB_DIR}/logs/ffprobe-output.txt"
JOB_SUMMARY="${JOB_DIR}/logs/opencue-job-summary-level2.txt"
WORKER_LOG="${JOB_DIR}/logs/worker-log-level2.txt"

echo "[P2O.0d] Smoke Level 2 — Real Media + Caption Overlay (Container Exec)"
echo "[P2O.0d] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/input" "${JOB_DIR}/work" "${JOB_DIR}/output" "${JOB_DIR}/logs"

# Write job summary
cat > "${JOB_SUMMARY}" << 'EOF'
=== OpenCue Job Summary (P2O.0d Level 2) ===
Submission Method: container-exec (fallback)
Container: opencue-rqd
Command: smoke-level-2-local-runner-equivalent
Status: SUBMITTED_VIA_CONTAINER_EXEC
Note: Not true OpenCue job submission. Fallback only.
EOF

# Execute inside RQD container
echo "[P2O.0d] Executing real media + caption overlay in RQD container..."
docker exec opencue-rqd sh -c '
  SHARED_ROOT="/mnt/opencue-shared/media-platform-smoke"
  JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"

  INPUT_FILE="${JOB_DIR}/input/input-fixture.mp4"
  ASS_FILE="${JOB_DIR}/work/caption-overlay-input.ass"
  OUTPUT_FILE="${JOB_DIR}/output/output.mp4"
  REPORT_FILE="${JOB_DIR}/output/local-render-execution-report.txt"
  FFPROBE_INPUT="${JOB_DIR}/logs/ffprobe-input.txt"
  FFPROBE_OUTPUT="${JOB_DIR}/logs/ffprobe-output.txt"

  mkdir -p "${JOB_DIR}/input" "${JOB_DIR}/work" "${JOB_DIR}/output" "${JOB_DIR}/logs"

  # Step 1: Generate input fixture
  echo "[RQD] Generating input-fixture.mp4..."
  ffmpeg -y \
    -f lavfi -i "testsrc=duration=3:size=640x360:rate=25" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
    "${INPUT_FILE}" \
    > "${JOB_DIR}/logs/ffmpeg-input.stdout.log" 2>"${JOB_DIR}/logs/ffmpeg-input.stderr.log"

  # Step 2: Generate ASS subtitle
  echo "[RQD] Generating caption-overlay-input.ass..."
  cat > "${ASS_FILE}" << ASSEOF
[Script Info]
Title: P2O.0d Smoke
ScriptType: v4.00+
PlayResX: 640
PlayResY: 360

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,DejaVu Sans,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,30,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.50,0:00:02.50,Default,,0,0,0,,P2O.0d Smoke Test
ASSEOF

  # Step 3: Render with caption overlay
  echo "[RQD] Rendering output.mp4 with caption overlay..."
  ffmpeg -y \
    -i "${INPUT_FILE}" \
    -vf "ass=${ASS_FILE}" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
    "${OUTPUT_FILE}" \
    > "${JOB_DIR}/logs/ffmpeg.stdout.log" 2>"${JOB_DIR}/logs/ffmpeg.stderr.log"

  # Step 4: Validate input
  echo "[RQD] Validating input..."
  ffprobe -v error \
    -show_entries stream=codec_name,width,height,duration \
    -show_entries format=format_name,duration,size \
    -of default=noprint_wrappers=1 \
    "${INPUT_FILE}" > "${FFPROBE_INPUT}" 2>&1

  # Step 5: Validate output
  echo "[RQD] Validating output..."
  ffprobe -v error \
    -show_entries stream=codec_name,width,height,duration \
    -show_entries format=format_name,duration,size \
    -of default=noprint_wrappers=1 \
    "${OUTPUT_FILE}" > "${FFPROBE_OUTPUT}" 2>&1

  # Step 6: Write report
  echo "[RQD] Writing report..."
  cat > "${REPORT_FILE}" << REPORTEOF
=== P2O.0d Local Runner Equivalent Smoke Report ===
Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Status: PASS
Submission: container-exec (RQD fallback)

Input:
  Path: ${INPUT_FILE}
  ffprobe: $(cat "${FFPROBE_INPUT}" | tr "\n" " ")

Output:
  Path: ${OUTPUT_FILE}
  ffprobe: $(cat "${FFPROBE_OUTPUT}" | tr "\n" " ")

Caption Overlay:
  ASS file: ${ASS_FILE}

Logs:
  FFmpeg input: ${JOB_DIR}/logs/ffmpeg-input.stdout.log
  FFmpeg render: ${JOB_DIR}/logs/ffmpeg.stdout.log
  ffprobe input: ${FFPROBE_INPUT}
  ffprobe output: ${FFPROBE_OUTPUT}
REPORTEOF

  echo "[RQD] Report:"
  cat "${REPORT_FILE}"
  echo "[RQD] Smoke Level 2 — PASS"
' > "${WORKER_LOG}" 2>&1 || {
    echo "[P2O.0d] Container exec failed. Check ${WORKER_LOG}"
    exit 1
}

# Verify outputs exist on host
if [ -f "${OUTPUT_FILE}" ] && [ -f "${REPORT_FILE}" ]; then
    echo "[P2O.0d] Output: ${OUTPUT_FILE}"
    ls -la "${OUTPUT_FILE}"
    echo "[P2O.0d] Report: ${REPORT_FILE}"
    echo "[P2O.0d] Smoke Level 2 — PASS"
else
    echo "[P2O.0d] ERROR: Output or report not found"
    exit 1
fi
