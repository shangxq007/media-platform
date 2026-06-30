#!/usr/bin/env bash
# P2O.0f — Generic CJSL submission via grpcurl
# Usage: submit-generic.sh <spec_file> <job_name> <preview_subdir> [expect_failure]
# If expect_failure=1, job is expected to fail (exit 1 / DEAD).
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
GRPCURL="${GRPCURL:-/tmp/grpcurl}"
PROTO_DIR="${PROTO_DIR:-/tmp/opencue-protos}"
CUEBOT_ADDR="localhost:8443"
PREVIEW_ROOT="${PREVIEW_ROOT:-build/opencue-shared/media-platform-smoke/preview/p2o0f}"

SPEC_FILE="${1:?Usage: submit-generic.sh <spec_file> <job_name> <preview_subdir> [expect_failure]}"
JOB_NAME="${2:?Usage: submit-generic.sh <spec_file> <job_name> <preview_subdir> [expect_failure]}"
PREVIEW_SUBDIR="${3:?Usage: submit-generic.sh <spec_file> <job_name> <preview_subdir> [expect_failure]}"
EXPECT_FAILURE="${4:-0}"

PREVIEW_DIR="${PREVIEW_ROOT}/${PREVIEW_SUBDIR}"
mkdir -p "${PREVIEW_DIR}"

echo "[P2O.0f] Submitting: ${JOB_NAME}"
echo "[P2O.0f] Spec: ${SPEC_FILE}"
echo "[P2O.0f] Preview: ${PREVIEW_DIR}"

# Read and encode spec
if [ ! -f "${SPEC_FILE}" ]; then
    echo "[P2O.0f] ERROR: Spec file not found: ${SPEC_FILE}"
    exit 1
fi

SPEC_JSON=$(cat "${SPEC_FILE}" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))')

# Submit via LaunchSpecAndWait
echo "[P2O.0f] Submitting via grpcurl..."
SUBMIT_RESPONSE=$("${GRPCURL}" -plaintext \
    -import-path "${PROTO_DIR}" \
    -proto job.proto \
    -d "{\"spec\": ${SPEC_JSON}}" \
    "${CUEBOT_ADDR}" \
    job.JobInterface.LaunchSpecAndWait 2>&1) || {
        echo "[P2O.0f] ERROR: grpcurl submission failed."
        echo "${SUBMIT_RESPONSE}"
        echo "${SUBMIT_RESPONSE}" > "${PREVIEW_DIR}/submit-error.txt"
        exit 1
    }

echo "[P2O.0f] Submission response:"
echo "${SUBMIT_RESPONSE}"
echo "${SUBMIT_RESPONSE}" > "${PREVIEW_DIR}/submit-response.json"

# Poll for completion
echo "[P2O.0f] Polling job status..."
MAX_WAIT=180
POLL_INTERVAL=5
ELAPSED=0
FINAL_STATUS=""

# Cuebot transforms job name: testing-{shot}-operator-{job_name} with hyphens→underscores
# Just search for the job name part after "p2o0f-"
JOB_NAME_PATTERN=$(echo "${JOB_NAME}" | sed 's/^p2o0f-//' | sed 's/-/_/g')

while [ "${ELAPSED}" -lt "${MAX_WAIT}" ]; do
    JOB_STATUS=$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -t -A -c \
        "SELECT str_state FROM job WHERE str_name LIKE '%${JOB_NAME_PATTERN}%' ORDER BY ts_started DESC LIMIT 1;" 2>/dev/null || echo "NOT_FOUND")

    echo "[P2O.0f] [${ELAPSED}s] Job status: ${JOB_STATUS}"

    case "${JOB_STATUS}" in
        SUCCEEDED|FINISHED)
            FINAL_STATUS="${JOB_STATUS}"
            break
            ;;
        DEAD|SETUP_FAILED|CHECKPOINTED)
            FINAL_STATUS="${JOB_STATUS}"
            break
            ;;
        *)
            sleep "${POLL_INTERVAL}"
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
            ;;
    esac
done

if [ -z "${FINAL_STATUS}" ]; then
    echo "[P2O.0f] TIMEOUT: Job did not complete within ${MAX_WAIT}s."
    FINAL_STATUS="TIMEOUT"
fi

echo "[P2O.0f] Final status: ${FINAL_STATUS}"

# Collect job summary
{
    echo "=== Job Summary: ${JOB_NAME} ==="
    echo "Final Status: ${FINAL_STATUS}"
    echo "Elapsed: ${ELAPSED}s"
    echo "Expect Failure: ${EXPECT_FAILURE}"
    echo ""
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -U opencue -d opencue -c \
        "SELECT str_name, str_state, str_show, str_shot, str_user, ts_started, ts_stopped FROM job WHERE str_name LIKE '%${JOB_NAME_PATTERN}%' ORDER BY ts_started DESC LIMIT 1;" 2>/dev/null
} > "${PREVIEW_DIR}/opencue-job-summary.txt"

# Collect frame status (use actual DB job name)
ACTUAL_JOB_NAME=$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U opencue -d opencue -t -A -c \
    "SELECT str_name FROM job WHERE str_name LIKE '%${JOB_NAME_PATTERN}%' ORDER BY ts_started DESC LIMIT 1;" 2>/dev/null || echo "")
bash docs/examples/opencue/local-docker-p2o0f/scripts/collect-frame-status.sh \
    "${ACTUAL_JOB_NAME}" "${PREVIEW_DIR}/frame-status.txt"

# Validate result
if [ "${EXPECT_FAILURE}" = "1" ]; then
    if [ "${FINAL_STATUS}" = "DEAD" ] || [ "${FINAL_STATUS}" = "SETUP_FAILED" ]; then
        echo "[P2O.0f] PASS: Job expected to fail, got ${FINAL_STATUS}."
        echo "RESULT: PASS (expected failure)" >> "${PREVIEW_DIR}/opencue-job-summary.txt"
        exit 0
    else
        echo "[P2O.0f] FAIL: Job expected to fail but got ${FINAL_STATUS}."
        echo "RESULT: FAIL (expected failure, got ${FINAL_STATUS})" >> "${PREVIEW_DIR}/opencue-job-summary.txt"
        exit 1
    fi
else
    if [ "${FINAL_STATUS}" = "SUCCEEDED" ] || [ "${FINAL_STATUS}" = "FINISHED" ]; then
        echo "[P2O.0f] PASS: Job ${FINAL_STATUS}."
        echo "RESULT: PASS" >> "${PREVIEW_DIR}/opencue-job-summary.txt"
        exit 0
    else
        echo "[P2O.0f] FAIL: Job status ${FINAL_STATUS}."
        echo "RESULT: FAIL (got ${FINAL_STATUS})" >> "${PREVIEW_DIR}/opencue-job-summary.txt"
        exit 1
    fi
fi
