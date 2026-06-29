#!/usr/bin/env bash
# P2O.0a — Prepare shared path for local Docker OpenCue smoke
# Operator-run manual testbed command only. Not production runtime.
# Usage: bash prepare-shared-path.sh [SHARED_ROOT]
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"

echo "[P2O.0a] Preparing shared path: ${SHARED_ROOT}"

mkdir -p "${SHARED_ROOT}/jobs/smoke-001/input"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/work"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/output"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/logs"

echo "[P2O.0a] Shared path layout created:"
find "${SHARED_ROOT}" -type d | sort

echo "[P2O.0a] Done."
