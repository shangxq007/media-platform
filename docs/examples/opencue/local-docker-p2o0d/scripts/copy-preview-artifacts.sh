#!/usr/bin/env bash
# P2O.0d — Copy preview artifacts from execution outputs to preview directory
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"
PREVIEW_ROOT="${SHARED_ROOT}/preview/p2o0d"
JOB_DIR="${SHARED_ROOT}/jobs/smoke-001"

echo "[P2O.0d] Copying preview artifacts..."

# Smoke Level 0
echo "  Copying smoke-level-0..."
cp -f "${JOB_DIR}/logs/shared-path-probe.txt" "${PREVIEW_ROOT}/smoke-level-0/" 2>/dev/null || echo "  WARN: shared-path-probe.txt not found"
cp -f "${JOB_DIR}/logs/opencue-job-summary.txt" "${PREVIEW_ROOT}/smoke-level-0/" 2>/dev/null || echo "  WARN: opencue-job-summary.txt not found"
cp -f "${JOB_DIR}/logs/worker-log.txt" "${PREVIEW_ROOT}/smoke-level-0/" 2>/dev/null || true

# Smoke Level 1
echo "  Copying smoke-level-1..."
cp -f "${JOB_DIR}/output/output.mp4" "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: output.mp4 not found"
cp -f "${JOB_DIR}/logs/ffprobe-output.txt" "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || echo "  WARN: ffprobe-output.txt not found"
cp -f "${JOB_DIR}/logs/ffmpeg.stdout.log" "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/ffmpeg.stderr.log" "${PREVIEW_ROOT}/smoke-level-1/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/opencue-job-summary-level1.txt" "${PREVIEW_ROOT}/smoke-level-1/opencue-job-summary.txt" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/worker-log-level1.txt" "${PREVIEW_ROOT}/smoke-level-1/worker-log.txt" 2>/dev/null || true

# Smoke Level 2
echo "  Copying smoke-level-2..."
cp -f "${JOB_DIR}/input/input-fixture.mp4" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: input-fixture.mp4 not found"
cp -f "${JOB_DIR}/work/caption-overlay-input.ass" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: caption-overlay-input.ass not found"
cp -f "${JOB_DIR}/output/output.mp4" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: output.mp4 not found"
cp -f "${JOB_DIR}/output/local-render-execution-report.txt" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || echo "  WARN: local-render-execution-report.txt not found"
cp -f "${JOB_DIR}/logs/ffprobe-input.txt" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/ffprobe-output.txt" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/ffmpeg.stdout.log" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/ffmpeg.stderr.log" "${PREVIEW_ROOT}/smoke-level-2/" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/opencue-job-summary-level2.txt" "${PREVIEW_ROOT}/smoke-level-2/opencue-job-summary.txt" 2>/dev/null || true
cp -f "${JOB_DIR}/logs/worker-log-level2.txt" "${PREVIEW_ROOT}/smoke-level-2/worker-log.txt" 2>/dev/null || true

echo "[P2O.0d] Preview artifacts copied to: ${PREVIEW_ROOT}"
find "${PREVIEW_ROOT}" -type f | sort
echo "[P2O.0d] Done."
