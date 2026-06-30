#!/usr/bin/env bash
# P2O.0e — Submit Smoke Level 2: Caption Overlay via Cuebot gRPC
# Submits a true OpenCue job that creates a 2-second green video with drawtext overlay.
# Waits for frame completion by polling database.
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
GRPCURL="${GRPCURL:-/tmp/grpcurl}"
PROTO_DIR="${PROTO_DIR:-/tmp/opencue-protos}"
CUEBOT_ADDR="localhost:8443"
PREVIEW_ROOT="${1:-build/opencue-shared/media-platform-smoke/preview/p2o0e}"

JOB_NAME="p2o0e-smoke-level-2"
SHOW="testing"
SHOT="p2o0e-probe"
FACILITY="local"
USER="operator"
OUTPUT_DIR="/mnt/opencue-shared/media-platform-smoke/preview/p2o0e/smoke-level-2"

echo "[P2O.0e] Smoke Level 2 — Caption Overlay (gRPC Submission)"
echo "[P2O.0e] Cuebot: ${CUEBOT_ADDR}"
echo "[P2O.0e] Job: ${JOB_NAME}"

# Pre-flight
if [ ! -x "${GRPCURL}" ]; then
    echo "[P2O.0e] ERROR: grpcurl not found at ${GRPCURL}"
    exit 1
fi

mkdir -p "${PREVIEW_ROOT}/smoke-level-2"

# Build CJSL XML spec for caption overlay job
# Creates a 2-second green video (color=green, 320x180, 25fps)
# with drawtext overlay "P2O.0e Smoke"
CJSL_SPEC='<?xml version="1.0" encoding="UTF-8"?>
<spec>
  <job name="'"${JOB_NAME}"'" shot="'"${SHOT}"'" show="'"${SHOW}"'" user="'"${USER}"'">
    <layer name="caption-overlay" type="RENDER">
      <cmd>
        <arg>mkdir -p '"${OUTPUT_DIR}"' &amp;&amp; ffmpeg -y -f lavfi -i "color=c=green:duration=2:size=320x180:rate=25" -vf "drawtext=text=P2O.0e Smoke:fontsize=24:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2" -c:v libx264 -preset ultrafast -pix_fmt yuv420p '"${OUTPUT_DIR}"'/output.mp4 2&gt; '"${OUTPUT_DIR}"'/ffmpeg.stderr.log &amp;&amp; ffprobe -v error -show_entries stream=codec_name,width,height,duration -show_entries format=format_name,duration,size -of default=noprint_wrappers=1 '"${OUTPUT_DIR}"'/output.mp4 &gt; '"${OUTPUT_DIR}"'/ffprobe-output.txt</arg>
      </cmd>
      <range>1-1</range>
      <chunk>1</chunk>
      <memory>100</memory>
      <cores>1</cores>
    </layer>
  </job>
</spec>'

echo "[P2O.0e] Submitting CJSL spec via grpcurl..."
echo "[P2O.0e] Spec:"
echo "${CJSL_SPEC}"
echo ""

# Submit via LaunchSpecAndWait
SUBMIT_RESPONSE=$("${GRPCURL}" -plaintext \
    -import-path "${PROTO_DIR}" \
    -proto job.proto \
    -d "{\"spec\": $(echo "${CJSL_SPEC}" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))')}" \
    "${CUEBOT_ADDR}" \
    job.JobInterface.LaunchSpecAndWait 2>&1) || {
        echo "[P2O.0e] ERROR: grpcurl submission failed."
        echo "${SUBMIT_RESPONSE}"
        exit 1
    }

echo "[P2O.0e] Submission response:"
echo "${SUBMIT_RESPONSE}"
echo ""

# Poll for completion
echo "[P2O.0e] Waiting for job completion (polling database)..."
MAX_WAIT=120
POLL_INTERVAL=5
ELAPSED=0

while [ "${ELAPSED}" -lt "${MAX_WAIT}" ]; do
    JOB_STATUS=$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -t -A -c \
        "SELECT str_state FROM job WHERE str_name='${JOB_NAME}' ORDER BY dt_submitted DESC LIMIT 1;" 2>/dev/null || echo "NOT_FOUND")

    echo "[P2O.0e] [${ELAPSED}s] Job status: ${JOB_STATUS}"

    case "${JOB_STATUS}" in
        SUCCEEDED)
            echo "[P2O.0e] Job SUCCEEDED."
            break
            ;;
        FINISHED)
            echo "[P2O.0e] Job FINISHED."
            break
            ;;
        DEAD|SETUP_FAILED|CHECKPOINTED)
            echo "[P2O.0e] ERROR: Job terminated with status: ${JOB_STATUS}"
            exit 1
            ;;
        *)
            sleep "${POLL_INTERVAL}"
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
            ;;
    esac
done

if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
    echo "[P2O.0e] ERROR: Job did not complete within ${MAX_WAIT}s."
    exit 1
fi

# Write job summary
JOB_SUMMARY_FILE="${PREVIEW_ROOT}/smoke-level-2/opencue-job-summary.txt"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U opencue -d opencue -c \
    "SELECT str_name, str_state, str_show, str_shot, str_user, dt_submitted, dt_started, dt_finished FROM job WHERE str_name='${JOB_NAME}' ORDER BY dt_submitted DESC LIMIT 1;" \
    > "${JOB_SUMMARY_FILE}" 2>/dev/null || echo "[P2O.0e] WARN: Could not write job summary"

# Verify output exists
if [ -f "${PREVIEW_ROOT}/smoke-level-2/output.mp4" ]; then
    echo "[P2O.0e] Output: ${PREVIEW_ROOT}/smoke-level-2/output.mp4"
    ls -la "${PREVIEW_ROOT}/smoke-level-2/output.mp4"
    echo "[P2O.0e] Smoke Level 2 — PASS"
else
    echo "[P2O.0e] WARN: Output not yet on host. Run copy-preview-artifacts.sh to retrieve from Docker volume."
    echo "[P2O.0e] Smoke Level 2 — SUBMITTED (output pending copy)"
fi
