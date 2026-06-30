#!/usr/bin/env bash
# P2O.0c — Check runtime health
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"

echo "[P2O.0c] Checking runtime health..."

# Check PostgreSQL
echo "--- PostgreSQL ---"
docker exec opencue-postgres pg_isready -U opencue 2>&1 && echo "PostgreSQL: READY" || echo "PostgreSQL: NOT READY"

# Check Cuebot
echo "--- Cuebot ---"
docker inspect --format='{{.State.Status}}' opencue-cuebot 2>/dev/null && echo "Cuebot container: RUNNING" || echo "Cuebot container: NOT FOUND"
docker logs opencue-cuebot --tail=20 2>&1 | tee /tmp/p2o0c-cuebot-health.log || true

# Check RQD
echo "--- RQD ---"
docker inspect --format='{{.State.Status}}' opencue-rqd 2>/dev/null && echo "RQD container: RUNNING" || echo "RQD container: NOT FOUND"
docker logs opencue-rqd --tail=20 2>&1 | tee /tmp/p2o0c-rqd-health.log || true

echo "[P2O.0c] Done."
