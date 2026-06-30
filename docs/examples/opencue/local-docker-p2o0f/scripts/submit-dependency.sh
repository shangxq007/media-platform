#!/usr/bin/env bash
# P2O.0f Scenario 5: Frame Dependency (inter-layer depends)
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/dependency.xml"

echo "[P2O.0f] === Scenario 5: Dependency ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-dependency" "dependency" 0
echo "[P2O.0f] === Scenario 5: COMPLETE ==="
