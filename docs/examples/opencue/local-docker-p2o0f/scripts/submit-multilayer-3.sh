#!/usr/bin/env bash
# P2O.0f Scenario 4: Multi-Layer Job (PREPROCESS + RENDER + POSTPROCESS)
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/multilayer-3.xml"

echo "[P2O.0f] === Scenario 4: Multi-Layer 3 ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-multilayer-3" "multilayer-3" 0
echo "[P2O.0f] === Scenario 4: COMPLETE ==="
