#!/usr/bin/env bash
# P2O.0f Scenario 1: Multi-Frame Job (10 frames, chunk=1)
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/multiframe-10.xml"

echo "[P2O.0f] === Scenario 1: Multi-Frame 10 ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-multiframe-10" "multiframe-10" 0
echo "[P2O.0f] === Scenario 1: COMPLETE ==="
