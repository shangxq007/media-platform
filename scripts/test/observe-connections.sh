#!/usr/bin/env bash
#
# Bounded peak-connection observer for AR-PTEH-CAP-03.
#
# Samples the live Testcontainers PostgreSQL connection count every 2 seconds for the
# requested duration, writing `epoch,count` lines to the log and reporting the PEAK.
# Designed to run ALONGSIDE the full module test run so the observed peak can be
# compared against the frozen V4 budget and usable capacity (97).
#
# Usage: observe-connections.sh <duration-seconds> <logfile>
#
# The sampler tolerates the container not existing yet (waits/retry), requires no sudo,
# modifies nothing, and never touches hermes-pg-test or any pre-provisioned database.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <duration-seconds> <logfile>" >&2
    exit 1
fi

DURATION="$1"
LOGFILE="$2"

if ! [[ "${DURATION}" =~ ^[0-9]+$ ]] || [[ "${DURATION}" -le 0 ]]; then
    echo "ERROR: duration-seconds must be a positive integer, got: ${DURATION}" >&2
    exit 1
fi

# --- locate the live Testcontainers PostgreSQL container ---
# Prefer a container built from the postgres:15-alpine image; fall back to the first
# org.testcontainers-managed postgres if the ancestor filter matches nothing.
get_container() {
    local cid
    cid=$(docker ps -q --filter "label=org.testcontainers=true" \
        --filter "ancestor=docker.io/library/postgres:15-alpine" 2>/dev/null | head -1)
    if [[ -z "${cid}" ]]; then
        cid=$(docker ps -q --filter "label=org.testcontainers=true" \
            --filter "ancestor=docker.io/library/postgres:16-alpine" 2>/dev/null | head -1)
    fi
    if [[ -z "${cid}" ]]; then
        cid=$(docker ps -q --filter "label=org.testcontainers=true" 2>/dev/null | head -1)
    fi
    echo "${cid}"
}

echo "[observe-connections] duration=${DURATION}s log=${LOGFILE}" >&2
echo "[observe-connections] waiting for the Testcontainers PostgreSQL container..." >&2

# Wait (bounded) for the container to appear.
container=""
for i in $(seq 1 60); do
    container=$(get_container)
    if [[ -n "${container}" ]]; then
        echo "[observe-connections] observing container ${container}" >&2
        break
    fi
    sleep 2
done

if [[ -z "${container}" ]]; then
    echo "[observe-connections] ERROR: no Testcontainers PostgreSQL container found" >&2
    echo "PEAK=0" >>"${LOGFILE}"
    echo "PEAK=0"
    exit 1
fi

# --- sample loop ---
peak=0
elapsed=0
echo "epoch,count" >"${LOGFILE}"

while [[ ${elapsed} -lt ${DURATION} ]]; do
    epoch=$(date +%s)
    count=$(docker exec "${container}" psql -U test -d media_platform_test -t -A -c \
        "SELECT count(*) FROM pg_stat_activity" 2>/dev/null | tr -d '[:space:]' || echo "0")
    # Guard against empty / non-numeric output.
    if ! [[ "${count}" =~ ^[0-9]+$ ]]; then
        count=0
    fi
    echo "${epoch},${count}" >>"${LOGFILE}"
    if [[ ${count} -gt ${peak} ]]; then
        peak=${count}
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done

echo "PEAK=${peak}" >>"${LOGFILE}"
echo "PEAK=${peak}"
echo "[observe-connections] done. peak=${peak} (budget V4 platform=74 / render=16, usable=97)" >&2
