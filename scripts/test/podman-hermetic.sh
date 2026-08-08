#!/usr/bin/env bash
#
# Repo-owned Podman service launcher for hermetic Testcontainers test execution.
#
# PTEH-V1: eliminates the historical 5-second idle-exit churn (the root cause of
# Testcontainers "Broken pipe" failures) by running `podman system service --time=0`
# (no idle timeout) on a dedicated socket. The service lifecycle is entirely
# repo-owned: it only manages its own PID file and socket, never touches systemd
# units, and never requires sudo.
#
# Commands:
#   start   Launch (or reuse) the hermetic service; print the socket path.
#   stop    Stop the recorded service and clean up orphaned Testcontainers
#           containers (those labelled org.testcontainers=true).
#   run     start, run "$@" with DOCKER_HOST set, then stop (trap on EXIT).
set -euo pipefail

SOCK_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id - u)}"
SOCK="${SOCK_DIR}/podman-hermetic.sock"
PIDFILE="${SOCK_DIR}/podman-hermetic.pid"

# --- readiness gate: is a podman service already answering on our socket? ---
socket_responds() {
    podman --url "unix://${SOCK}" info >/dev/null 2>&1
}

start() {
    if socket_responds; then
        echo "[podman-hermetic] reusing running service at ${SOCK}" >&2
        echo "${SOCK}"
        return 0
    fi

    # Start a command-scoped service with NO idle timeout (--time=0).
    echo "[podman-hermetic] starting service at ${SOCK} (--time=0)" >&2
    podman system service --time=0 "unix://${SOCK}" >/dev/null 2>&1 &
    pid=$!
    echo "${pid}" >"${PIDFILE}"

    # Wait (max ~10s) for the socket to become ready.
    local i
    for i in $(seq 1 20); do
        if socket_responds; then
            echo "[podman-hermetic] service ready at ${SOCK}" >&2
            echo "${SOCK}"
            return 0
        fi
        # exit early if the service already died
        if ! kill -0 "${pid}" 2>/dev/null; then
            echo "[podman-hermetic] service (pid ${pid}) exited unexpectedly" >&2
            rm -f "${PIDFILE}"
            return 1
        fi
        sleep 0.5
    done

    echo "[podman-hermetic] ERROR: service did not become ready at ${SOCK} within ~10s" >&2
    return 1
}

stop() {
    # Stop the recorded service (if any).
    if [[ -f "${PIDFILE}" ]]; then
        local pid
        pid=$(cat "${PIDFILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            echo "[podman-hermetic] stopping service (pid ${pid})" >&2
            kill "${pid}" 2>/dev/null || true
            wait "${pid}" 2>/dev/null || true
        fi
        rm -f "${PIDFILE}"
    else
        echo "[podman-hermetic] no PID file; nothing to stop" >&2
    fi

    # Clean up orphaned Testcontainers containers (only those labelled by TC).
    if socket_responds; then
        local orphans
        orphans=$(podman --url "unix://${SOCK}" ps -aq --filter label=org.testcontainers=true 2>/dev/null || true)
        if [[ -n "${orphans}" ]]; then
            local count=0
            while IFS= read -r c; do
                [[ -n "${c}" ]] || continue
                podman --url "unix://${SOCK}" rm -f "${c}" >/dev/null 2>&1 || true
                count=$((count + 1))
            done <<<"${orphans}"
            echo "[podman-hermetic] cleaned up ${count} orphaned Testcontainers container(s)" >&2
        fi
    fi
}

# run <cmd...>: start, export DOCKER_HOST, run the command, stop on EXIT.
run() {
    if [[ $# -eq 0 ]]; then
        echo "[podman-hermetic] ERROR: run requires a command" >&2
        return 1
    fi

    local sock
    sock=$(start) || return 1

    export DOCKER_HOST="unix://${sock}"
    echo "[podman-hermetic] DOCKER_HOST=${DOCKER_HOST}" >&2

    # Ensure cleanup even on failure.
    trap 'stop' EXIT

    "$@"
}

case "${1:-}" in
    start)
        shift
        start
        ;;
    stop)
        shift
        stop
        ;;
    run)
        shift
        run "$@"
        ;;
    *)
        echo "Usage: $0 {start|stop|run <cmd...>}" >&2
        exit 1
        ;;
esac
