#!/usr/bin/env bash
# P2O.0c — Prepare shared path for runtime readiness
# Operator-run only. Not production.
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"

echo "[P2O.0c] Preparing shared path: ${SHARED_ROOT}"

mkdir -p "${SHARED_ROOT}/jobs/smoke-001/input"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/work"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/output"
mkdir -p "${SHARED_ROOT}/jobs/smoke-001/logs"

echo "[P2O.0c] Shared path prepared:"
find "${SHARED_ROOT}" -type d | sort
echo "[P2O.0c] Done."
