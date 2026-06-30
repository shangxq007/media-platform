#!/usr/bin/env bash
# P2O.0d — Prepare job submission smoke directories
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"

echo "[P2O.0d] Preparing job submission smoke: ${SHARED_ROOT}"

# Execution directories
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/input"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/work"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/output"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/logs"

# Preview directories
mkdir -p "${SHARED_ROOT}/preview/p2o0d/smoke-level-0"
mkdir -p "${SHARED_ROOT}/preview/p2o0d/smoke-level-1"
mkdir -p "${SHARED_ROOT}/preview/p2o0d/smoke-level-2"

echo "[P2O.0d] Directories prepared:"
find "${SHARED_ROOT}" -type d -maxdepth 4 | sort
echo "[P2O.0d] Done."
