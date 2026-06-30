#!/usr/bin/env bash
# P2O.0c — Start OpenCue runtime
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"

echo "[P2O.0c] Starting OpenCue runtime..."

# Check prerequisites
if ! docker image inspect opencue-rqd-smoke:local >/dev/null 2>&1; then
    echo "[P2O.0c] ERROR: opencue-rqd-smoke:local image not found. Build it first."
    exit 1
fi

docker compose -f "${COMPOSE_FILE}" up -d > /tmp/p2o0c-start-runtime.log 2>&1
echo "[P2O.0c] Runtime started. Log: /tmp/p2o0c-start-runtime.log"

echo "[P2O.0c] Waiting 10s for services to initialize..."
sleep 10

docker compose -f "${COMPOSE_FILE}" ps
echo "[P2O.0c] Done."
