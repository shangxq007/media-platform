#!/usr/bin/env bash
# P2O.0c — Stop runtime
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"

echo "[P2O.0c] Stopping OpenCue runtime..."
docker compose -f "${COMPOSE_FILE}" down -v
echo "[P2O.0c] Done."
