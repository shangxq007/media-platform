#!/usr/bin/env bash
# PFIRR1-B3: deterministic test-runtime provisioning for GitHub-hosted runners.
#
# Provides the docker-compatible container API contract required by
# PostgresTestContainerSupport and HermeticPodmanServiceContractTest (PTEH-V1):
#   - a unix-socket Docker-compatible API at DOCKER_HOST
#   - /_ping responds HTTP 200 with Api-Version >= 1.41
#   - the service survives the historical idle window (no 5s churn; --time=0)
# plus the ffmpeg binary required by render-boundary media fixtures
# (MinimalMediaRenderBoundaryTest / StartClaimAndFailureDurabilityTest).
#
# Strategy (deterministic, fail closed):
#   1) install ffmpeg if missing
#   2) prefer the runner's pre-installed Docker daemon (docker-compatible API);
#      otherwise start the repo-owned hermetic Podman service (--time=0),
#      mirroring scripts/test/podman-hermetic.sh semantics
#   3) validate the PTEH-V1 contract (socket, /_ping 200, Api-Version >= 1.41)
#   4) persist DOCKER_HOST for subsequent workflow steps (GITHUB_ENV)
set -euo pipefail

# ── 1) ffmpeg (render-boundary tests generate a media fixture via ProcessBuilder) ──
if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "[ci-test-runtime] ffmpeg missing; installing"
    sudo apt-get update -qq
    sudo apt-get install -y -qq ffmpeg
fi
command -v ffmpeg >/dev/null 2>&1 || { echo "FAIL: ffmpeg unavailable after install" >&2; exit 1; }
echo "[ci-test-runtime] ffmpeg present: $(ffmpeg -version | head -1)"

# ── 2) container API (deterministic ladder) ──
pick_container_host() {
    # a) explicit DOCKER_HOST already pointing at a live socket
    if [[ -n "${DOCKER_HOST:-}" ]]; then
        local s="${DOCKER_HOST#unix://}"
        if [[ -S "$s" ]]; then echo "$DOCKER_HOST"; return 0; fi
    fi
    # b) real docker CLI (context host, then default socket)
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        local h
        h="$(docker context inspect "$(docker context show 2>/dev/null)" --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
        case "$h" in
            unix://*) [[ -S "${h#unix://}" ]] && { echo "$h"; return 0; } ;;
            /*) [[ -S "$h" ]] && { echo "unix://$h"; return 0; } ;;
        esac
        [[ -S /var/run/docker.sock ]] && { echo "unix:///var/run/docker.sock"; return 0; }
    fi
    # c) podman-compatible socket already present (incl. podman-docker shim)
    if command -v podman >/dev/null 2>&1; then
        local dir="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
        local s
        for s in "${dir}/podman/podman.sock" "${dir}/podman-hermetic.sock"; do
            if [[ -S "$s" ]] && podman --url "unix://$s" info >/dev/null 2>&1; then
                echo "unix://$s"; return 0
            fi
        done
    fi
    return 1
}

DOCKER_HOST="$(pick_container_host || true)"
if [[ -z "$DOCKER_HOST" ]]; then
    # d) start the repo-owned hermetic podman service (--time=0, no idle churn)
    if ! command -v podman >/dev/null 2>&1; then
        echo "[ci-test-runtime] podman missing; installing"
        sudo apt-get update -qq
        sudo apt-get install -y -qq podman
    fi
    local_dir="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
    sock="${local_dir}/podman-hermetic.sock"
    echo "[ci-test-runtime] starting hermetic podman service (--time=0) at ${sock}"
    podman system service --time=0 "unix://${sock}" >/dev/null 2>&1 &
    for _ in $(seq 1 20); do
        if podman --url "unix://${sock}" info >/dev/null 2>&1; then break; fi
        sleep 0.5
    done
    podman --url "unix://${sock}" info >/dev/null 2>&1 \
        || { echo "FAIL: hermetic podman service not ready at ${sock}" >&2; exit 1; }
    DOCKER_HOST="unix://${sock}"
    echo "[ci-test-runtime] using hermetic podman service"
fi
export DOCKER_HOST
echo "[ci-test-runtime] container API at ${DOCKER_HOST}"

# ── 3) PTEH-V1 pre-test validation (fail closed) ──
SOCKET="${DOCKER_HOST#unix://}"
[[ -S "$SOCKET" ]] || { echo "FAIL: container API socket missing: ${SOCKET}" >&2; exit 1; }

RESP="$(curl -sS -i --max-time 15 --unix-socket "$SOCKET" http://localhost/_ping)" \
    || { echo "FAIL: /_ping unreachable at ${DOCKER_HOST}" >&2; exit 1; }

case "$RESP" in
    HTTP/1.1*200*) ;;
    *) echo "FAIL: /_ping did not return HTTP 200: ${RESP%%$'\n'*}" >&2; exit 1 ;;
esac

API_VERSION="$(printf '%s\n' "$RESP" | sed -n 's/^[Aa]pi-[Vv]ersion:[[:space:]]*\([0-9][0-9.]*\).*/\1/p' | head -1)"
[[ -n "$API_VERSION" ]] || { echo "FAIL: /_ping response missing Api-Version header" >&2; exit 1; }

major="${API_VERSION%%.*}"
minor="${API_VERSION#*.}"
minor="${minor%%.*}"
if [[ "$major" -lt 1 ]] || { [[ "$major" -eq 1 ]] && [[ "$minor" -lt 41 ]]; }; then
    echo "FAIL: Api-Version ${API_VERSION} < 1.41 (PTEH-V1 contract)" >&2
    exit 1
fi
echo "[ci-test-runtime] OK: DOCKER_HOST=${DOCKER_HOST} Api-Version=${API_VERSION}"

# ── 4) persist for subsequent workflow steps ──
if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "DOCKER_HOST=${DOCKER_HOST}" >> "$GITHUB_ENV"
fi
