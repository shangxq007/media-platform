#!/usr/bin/env bash
# P2O.0f Scenario 3: Multi-Layer Job (PREPROCESS + RENDER)
# Operator-run only. Not production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPEC_FILE="${SCRIPT_DIR}/../specs/multilayer-2.xml"

echo "[P2O.0f] === Scenario 3: Multi-Layer 2 ==="
bash "${SCRIPT_DIR}/submit-generic.sh" "${SPEC_FILE}" "p2o0f-multilayer-2" "multilayer-2" 0
echo "[P2O.0f] === Scenario 3: COMPLETE ==="
