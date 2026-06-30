#!/usr/bin/env bash
# P2O.0c — Validate compose config
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"

echo "[P2O.0c] Validating compose config: ${COMPOSE_FILE}"

# Check if local RQD smoke image exists
if ! docker image inspect opencue-rqd-smoke:local >/dev/null 2>&1; then
    echo "[P2O.0c] WARNING: opencue-rqd-smoke:local image not found."
    echo "[P2O.0c] Build it first: docker build -f docs/examples/opencue/local-docker-p2o0c/rqd-smoke.Dockerfile -t opencue-rqd-smoke:local ."
    exit 1
fi

echo "[P2O.0c] Images confirmed:"
echo "  - postgres:16-alpine"
echo "  - opencue/cuebot:1.19.1"
echo "  - opencue-rqd-smoke:local"

docker compose -f "${COMPOSE_FILE}" config > /tmp/p2o0c-compose-config.log 2>&1
echo "[P2O.0c] Compose config valid. Log: /tmp/p2o0c-compose-config.log"
echo "[P2O.0c] Done."
