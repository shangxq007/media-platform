#!/usr/bin/env bash
#
# State-aware bounded peak-connection observer for AR-PTEH-CAP-03 (V4).
#
# Samples the live Testcontainers PostgreSQL connection count every 2 seconds for the
# requested duration, breaking the total into active / idle / idle-in-transaction /
# idle-in-transaction-aborted, writing `epoch,total,active,idle,idle_in_txn,idle_in_txn_aborted`
# lines to the log and reporting the PEAK of each. Designed to run ALONGSIDE the full
# module test run so the observed peak can be compared against the frozen V4 budget and
# usable capacity (97), and so persistent idle-in-transaction accumulation (a connection
# leak signature) can be detected.
#
# Usage: observe-connections-state.sh <duration-seconds> <logfile>
#
# CRITICAL container-selection rule: this sampler latches ONLY the execution-owned
# PostgreSQL container, identified by ANCESTOR postgres image (15-alpine, then 16-alpine).
# It must NEVER fall back to the generic `label=org.testcontainers=true` filter, because
# the Ryuk container ALSO carries org.testcontainers=true and would corrupt the count.
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

# --- locate ONLY the execution-owned PostgreSQL container by ancestor image ---
# NEVER fall back to the generic org.testcontainers=true filter: the Ryuk container also
# carries that label and would be wrongly counted. If no postgres ancestor is found, fail.
get_postgres_container() {
    local cid
    cid=$(docker ps -q --filter "label=org.testcontainers=true" \
        --filter "ancestor=docker.io/library/postgres:15-alpine" 2>/dev/null | head -1)
    if [[ -z "${cid}" ]]; then
        cid=$(docker ps -q --filter "label=org.testcontainers=true" \
            --filter "ancestor=docker.io/library/postgres:16-alpine" 2>/dev/null | head -1)
    fi
    echo "${cid}"
}

echo "[observe-connections-state] duration=${DURATION}s log=${LOGFILE}" >&2
echo "[observe-connections-state] waiting for the Testcontainers PostgreSQL container (by ancestor image ONLY)..." >&2

# Wait (bounded) for the postgres container to appear.
container=""
for i in $(seq 1 60); do
    container=$(get_postgres_container)
    if [[ -n "${container}" ]]; then
        echo "[observe-connections-state] observing postgres container ${container}" >&2
        break
    fi
    sleep 2
done

if [[ -z "${container}" ]]; then
    echo "[observe-connections-state] ERROR: no Testcontainers PostgreSQL container found (by ancestor image)" >&2
    echo "PEAK_TOTAL=0 PEAK_ACTIVE=0 PEAK_IDLE=0 PEAK_IDLE_IN_TXN=0 PEAK_IDLE_IN_TXN_ABORTED=0" >>"${LOGFILE}"
    echo "PEAK_TOTAL=0"
    exit 1
fi

Q='SELECT
      count(*) AS total,
      count(*) FILTER (WHERE state = '\''active'\'') AS active,
      count(*) FILTER (WHERE state = '\''idle'\'') AS idle,
      count(*) FILTER (WHERE state = '\''idle in transaction'\'') AS idle_in_txn,
      count(*) FILTER (WHERE state = '\''idle in transaction (aborted)'\'') AS idle_in_txn_aborted
    FROM pg_stat_activity'

# --- sample loop ---
peak_total=0 peak_active=0 peak_idle=0 peak_idle_in_txn=0 peak_idle_in_txn_aborted=0
elapsed=0
echo "epoch,total,active,idle,idle_in_txn,idle_in_txn_aborted" >"${LOGFILE}"

while [[ ${elapsed} -lt ${DURATION} ]]; do
    epoch=$(date +%s)
    row=$(docker exec "${container}" psql -U test -d media_platform_test -t -A -F',' -c \
        "${Q}" 2>/dev/null || echo "0,0,0,0,0")
    IFS=',' read -r total active idle idle_in_txn idle_in_txn_aborted <<<"${row}"
    # Guard against empty / non-numeric output.
    for v in total active idle idle_in_txn idle_in_txn_aborted; do
        if ! [[ "${!v}" =~ ^[0-9]+$ ]]; then
            declare "$v=0"
        fi
    done
    echo "${epoch},${total},${active},${idle},${idle_in_txn},${idle_in_txn_aborted}" >>"${LOGFILE}"
    [[ ${total} -gt ${peak_total} ]] && peak_total=${total}
    [[ ${active} -gt ${peak_active} ]] && peak_active=${active}
    [[ ${idle} -gt ${peak_idle} ]] && peak_idle=${idle}
    [[ ${idle_in_txn} -gt ${peak_idle_in_txn} ]] && peak_idle_in_txn=${idle_in_txn}
    [[ ${idle_in_txn_aborted} -gt ${peak_idle_in_txn_aborted} ]] && peak_idle_in_txn_aborted=${idle_in_txn_aborted}
    sleep 2
    elapsed=$((elapsed + 2))
done

{
    echo "PEAK_TOTAL=${peak_total}"
    echo "PEAK_ACTIVE=${peak_active}"
    echo "PEAK_IDLE=${peak_idle}"
    echo "PEAK_IDLE_IN_TXN=${peak_idle_in_txn}"
    echo "PEAK_IDLE_IN_TXN_ABORTED=${peak_idle_in_txn_aborted}"
} >>"${LOGFILE}"

echo "PEAK_TOTAL=${peak_total}"
echo "[observe-connections-state] done. peak total=${peak_total} active=${peak_active} idle=${peak_idle} idle_in_txn=${peak_idle_in_txn} idle_in_txn_aborted=${peak_idle_in_txn_aborted} (budget V4 platform=74 / render=16, usable=97)" >&2
