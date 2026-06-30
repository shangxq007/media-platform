#!/usr/bin/env bash
# P2O.0f Scenario 7: Mixed Success/Failure
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/mixed-failure.xml"

echo "[P2O.0f] === Scenario 7: Mixed Failure ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-mixed-failure" "mixed-failure" 1
echo "[P2O.0f] === Scenario 7: COMPLETE ==="
