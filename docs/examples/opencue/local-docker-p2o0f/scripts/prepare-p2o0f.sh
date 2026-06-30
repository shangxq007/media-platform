#!/usr/bin/env bash
# P2O.0f — Prepare: verify prerequisites
# Checks grpcurl, proto files, Docker runtime health.
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
GRPCURL="${GRPCURL:-/tmp/grpcurl}"
PROTO_DIR="${PROTO_DIR:-/tmp/opencue-protos}"

echo "[P2O.0f] Checking prerequisites..."

# grpcurl
if [ ! -x "${GRPCURL}" ]; then
    echo "[P2O.0f] FAIL: grpcurl not found at ${GRPCURL}"
    exit 1
fi
echo "[P2O.0f] OK: grpcurl at ${GRPCURL}"

# Proto files
if [ ! -d "${PROTO_DIR}" ]; then
    echo "[P2O.0f] FAIL: proto dir not found at ${PROTO_DIR}"
    exit 1
fi
PROTO_COUNT=$(find "${PROTO_DIR}" -name "*.proto" | wc -l)
if [ "${PROTO_COUNT}" -lt 10 ]; then
    echo "[P2O.0f] FAIL: expected 10+ proto files, found ${PROTO_COUNT}"
    exit 1
fi
echo "[P2O.0f] OK: ${PROTO_COUNT} proto files in ${PROTO_DIR}"

# Docker compose
if ! docker compose -f "${COMPOSE_FILE}" config > /dev/null 2>&1; then
    echo "[P2O.0f] FAIL: compose config invalid"
    exit 1
fi
echo "[P2O.0f] OK: compose config valid"

# Docker runtime health
CONTAINERS=$(docker compose -f "${COMPOSE_FILE}" ps --format '{{.Name}} {{.Status}}' 2>/dev/null || echo "")
if [ -z "${CONTAINERS}" ]; then
    echo "[P2O.0f] WARN: no containers running. Start with:"
    echo "  bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh"
    exit 1
fi
echo "[P2O.0f] OK: containers running"
echo "${CONTAINERS}"

# Cuebot gRPC reachable
if ! "${GRPCURL}" -plaintext -import-path "${PROTO_DIR}" -proto show.proto \
    localhost:8443 show.ShowInterface.GetShows > /dev/null 2>&1; then
    echo "[P2O.0f] FAIL: Cuebot gRPC not reachable"
    exit 1
fi
echo "[P2O.0f] OK: Cuebot gRPC reachable"

echo "[P2O.0f] All prerequisites OK."
