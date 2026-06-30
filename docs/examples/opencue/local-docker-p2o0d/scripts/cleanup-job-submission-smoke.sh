#!/usr/bin/env bash
# P2O.0d — Cleanup job submission smoke
set -euo pipefail

SHARED_ROOT="${1:-build/opencue-shared/media-platform-smoke}"

echo "[P2O.0d] Cleaning up job submission smoke: ${SHARED_ROOT}"

# Remove execution outputs (keep preview artifacts)
rm -rf "${SHARED_ROOT}/jobs/smoke-001/input"
rm -rf "${SHARED_ROOT}/jobs/smoke-001/work"
rm -rf "${SHARED_ROOT}/jobs/smoke-001/output"
rm -rf "${SHARED_ROOT}/jobs/smoke-001/logs"

echo "[P2O.0d] Execution outputs cleaned. Preview artifacts preserved."
echo "[P2O.0d] Done."
