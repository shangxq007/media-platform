#!/usr/bin/env bash
# P2O.0e — Inspect Cuebot gRPC Surface via grpcurl
# Lists services, checks reflection, lists host/show/allocation.
# Operator-run only. Not production.
set -euo pipefail

COMPOSE_FILE="docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml"
GRPCURL="${GRPCURL:-/tmp/grpcurl}"
PROTO_DIR="${PROTO_DIR:-/tmp/opencue-protos}"
CUEBOT_ADDR="localhost:8443"

echo "[P2O.0e] Inspect Cuebot gRPC Surface"
echo "[P2O.0e] Compose file: ${COMPOSE_FILE}"
echo "[P2O.0e] grpcurl: ${GRPCURL}"
echo "[P2O.0e] Proto dir: ${PROTO_DIR}"
echo ""

# Pre-flight checks
if [ ! -x "${GRPCURL}" ]; then
    echo "[P2O.0e] ERROR: grpcurl not found or not executable at ${GRPCURL}"
    echo "[P2O.0e] Install: go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest"
    exit 1
fi

if [ ! -d "${PROTO_DIR}" ]; then
    echo "[P2O.0e] ERROR: Proto directory not found at ${PROTO_DIR}"
    echo "[P2O.0e] Clone OpenCue protos: git clone https://github.com/AcademySoftwareFoundation/OpenCue.git /tmp/opencue-protos-src"
    echo "[P2O.0e] Then copy proto/ directory to ${PROTO_DIR}"
    exit 1
fi

# Check Cuebot is reachable
echo "[P2O.0e] Checking Cuebot reachability at ${CUEBOT_ADDR}..."
if ! docker compose -f "${COMPOSE_FILE}" ps cuebot --format '{{.State}}' 2>/dev/null | grep -q "running"; then
    echo "[P2O.0e] ERROR: Cuebot container is not running."
    echo "[P2O.0e] Start runtime: bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh"
    exit 1
fi
echo "[P2O.0e] Cuebot container is running."
echo ""

# Step 1: Check gRPC reflection
echo "=== Step 1: gRPC Reflection Check ==="
if "${GRPCURL}" -plaintext "${CUEBOT_ADDR}" list 2>/dev/null; then
    echo "[P2O.0e] gRPC reflection is ENABLED."
else
    echo "[P2O.0e] gRPC reflection is NOT enabled (expected for Cuebot)."
    echo "[P2O.0e] Using -import-path and -proto for all subsequent calls."
fi
echo ""

# Step 2: List known Cuebot services via proto import
echo "=== Step 2: List Cuebot Services (via proto import) ==="
SERVICES=(
    "job.JobInterface"
    "host.HostInterface"
    "show.ShowInterface"
    "allocation.AllocationInterface"
    "shot.ShotInterface"
    "layer.LayerInterface"
    "frame.FrameInterface"
    "service.ServiceInterface"
)

for svc in "${SERVICES[@]}"; do
    SVC_PKG="${svc%%.*}"
    SVC_FILE="${PROTO_DIR}/${SVC_PKG}.proto"
    if [ -f "${SVC_FILE}" ]; then
        echo "  FOUND: ${svc} (${SVC_FILE})"
    else
        echo "  MISSING: ${svc} (expected ${SVC_FILE})"
    fi
done
echo ""

# Step 3: List hosts
echo "=== Step 3: List Hosts ==="
HOST_PROTO="${PROTO_DIR}/host.proto"
if [ -f "${HOST_PROTO}" ]; then
    "${GRPCURL}" -plaintext \
        -import-path "${PROTO_DIR}" \
        -proto host.proto \
        "${CUEBOT_ADDR}" \
        host.HostInterface.GetHosts 2>&1 || {
        echo "[P2O.0e] WARN: GetHosts call failed (may require running hosts)"
    }
else
    echo "[P2O.0e] SKIP: host.proto not found"
fi
echo ""

# Step 4: List shows
echo "=== Step 4: List Shows ==="
SHOW_PROTO="${PROTO_DIR}/show.proto"
if [ -f "${SHOW_PROTO}" ]; then
    "${GRPCURL}" -plaintext \
        -import-path "${PROTO_DIR}" \
        -proto show.proto \
        "${CUEBOT_ADDR}" \
        show.ShowInterface.GetShows 2>&1 || {
        echo "[P2O.0e] WARN: GetShows call failed"
    }
else
    echo "[P2O.0e] SKIP: show.proto not found"
fi
echo ""

# Step 5: List allocations
echo "=== Step 5: List Allocations ==="
ALLOC_PROTO="${PROTO_DIR}/allocation.proto"
if [ -f "${ALLOC_PROTO}" ]; then
    "${GRPCURL}" -plaintext \
        -import-path "${PROTO_DIR}" \
        -proto allocation.proto \
        "${CUEBOT_ADDR}" \
        allocation.AllocationInterface.GetAllocations 2>&1 || {
        echo "[P2O.0e] WARN: GetAllocations call failed"
    }
else
    echo "[P2O.0e] SKIP: allocation.proto not found"
fi
echo ""

echo "[P2O.0e] gRPC surface inspection complete."
