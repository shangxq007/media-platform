#!/usr/bin/env bash
# P2O.0e — Prepare shared path directories for gRPC submission smoke
# Creates preview and execution directories for p2o0e smoke output.
# Operator-run only. Not production.
set -euo pipefail

PREVIEW_ROOT="${1:-build/opencue-shared/media-platform-smoke/preview/p2o0e}"

echo "[P2O.0e] Preparing gRPC submission smoke directories"
echo "[P2O.0e] Preview root: ${PREVIEW_ROOT}"

# Preview directories (host-side output staging)
mkdir -p "${PREVIEW_ROOT}/smoke-level-0"
mkdir -p "${PREVIEW_ROOT}/smoke-level-1"
mkdir -p "${PREVIEW_ROOT}/smoke-level-2"

echo "[P2O.0e] Preview directories prepared:"
find "${PREVIEW_ROOT}" -type d | sort

echo "[P2O.0e] Done."
