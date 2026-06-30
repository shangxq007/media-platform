#!/usr/bin/env bash
# P2O.0f Scenario 2: Multi-Frame Job (20 frames, chunk=5)
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/multiframe-20-chunk5.xml"

echo "[P2O.0f] === Scenario 2: Multi-Frame 20 Chunk=5 ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-multiframe-20c5" "multiframe-20-chunk5" 0
echo "[P2O.0f] === Scenario 2: COMPLETE ==="
