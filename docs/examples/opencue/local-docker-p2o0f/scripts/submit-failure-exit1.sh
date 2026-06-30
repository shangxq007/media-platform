#!/usr/bin/env bash
# P2O.0f Scenario 6: Failure Visibility — Intentional Non-Zero Exit
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/failure-exit1.xml"

echo "[P2O.0f] === Scenario 6: Failure Exit 1 ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-failure-exit1" "failure-exit1" 1
echo "[P2O.0f] === Scenario 6: COMPLETE ==="
