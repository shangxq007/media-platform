#!/usr/bin/env bash
# P2O.0d — Submit Smoke Level 0: Shared Path Probe via RQD Container Exec
# Fallback: runs inside RQD container, not true OpenCue job submission.
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
LOG_FILE="${JOB_DIR}/logs/shared-path-probe.txt"
JOB_SUMMARY="${JOB_DIR}/logs/opencue-job-summary.txt"
WORKER_LOG="${JOB_DIR}/logs/worker-log.txt"

echo "[P2O.0d] Smoke Level 0 — Shared Path Probe (Container Exec)"
echo "[P2O.0d] Shared root: ${SHARED_ROOT}"

mkdir -p "${JOB_DIR}/logs"

# Write job summary
cat > "${JOB_SUMMARY}" << 'EOF'
=== OpenCue Job Summary (P2O.0d Level 0) ===
Submission Method: container-exec (fallback)
Container: opencue-rqd
Command: smoke-level-0-shared-path-probe
Status: SUBMITTED_VIA_CONTAINER_EXEC
Note: Not true OpenCue job submission. Fallback only.
EOF

# Execute inside RQD container
echo "[P2O.0d] Executing shared path probe in RQD container..."
docker exec opencue-rqd sh -c '
  SHARED_ROOT="/mnt/opencue-shared/media-platform-smoke"
  JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"
  LOG_FILE="${JOB_DIR}/logs/shared-path-probe.txt"

  mkdir -p "${JOB_DIR}/logs"

  {
    echo "=== P2O.0d Runtime Shared Path Probe (via RQD) ==="
    echo "Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "Hostname: $(hostname)"
    echo "Whoami: $(whoami)"
    echo "Pwd: $(pwd)"
    echo "Container: opencue-rqd"
    echo "Job dir exists: $(test -d "${JOB_DIR}" && echo YES || echo NO)"
    echo "Input dir exists: $(test -d "${JOB_DIR}/input" && echo YES || echo NO)"
    echo "Work dir exists: $(test -d "${JOB_DIR}/work" && echo YES || echo NO)"
    echo "Output dir exists: $(test -d "${JOB_DIR}/output" && echo YES || echo NO)"
    echo "Logs dir exists: $(test -d "${JOB_DIR}/logs" && echo YES || echo NO)"
    echo "FFmpeg available: $(command -v ffmpeg >/dev/null 2>&1 && echo YES || echo NO)"
    echo "ffprobe available: $(command -v ffprobe >/dev/null 2>&1 && echo YES || echo NO)"
    echo "=== Probe complete ==="
  } > "${LOG_FILE}" 2>&1

  cat "${LOG_FILE}"
' > "${WORKER_LOG}" 2>&1 || {
    echo "[P2O.0d] Container exec failed. Check ${WORKER_LOG}"
    exit 1
}

# Verify output exists on host
if [ -f "${LOG_FILE}" ]; then
    echo "[P2O.0d] Probe written to: ${LOG_FILE}"
    cat "${LOG_FILE}"
    echo "[P2O.0d] Smoke Level 0 — PASS"
else
    echo "[P2O.0d] ERROR: Probe file not found on host: ${LOG_FILE}"
    exit 1
fi
