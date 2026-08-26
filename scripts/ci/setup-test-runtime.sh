#!/usr/bin/env bash
# PFIRR1-B3: deterministic test-runtime provisioning for GitHub-hosted runners.
#
# Provides the docker-compatible container API contract required by
# PostgresTestContainerSupport and HermeticPodmanServiceContractTest (PTEH-V1):
#   - a unix-socket Docker-compatible API at DOCKER_HOST
#   - /_ping responds HTTP 200 with Api-Version >= 1.41
#   - the service survives the historical idle window (no 5s churn; --time=0)
# plus the host binaries required by render-boundary media fixtures and local
# sandbox capability detection:
#   - ffmpeg for MinimalMediaRenderBoundaryTest / StartClaimAndFailureDurabilityTest
#   - /usr/bin/bwrap for the BubblewrapSandboxCapabilityDetector production shape
# The bwrap contract is a real, fail-closed namespace probe: unshare all
# namespaces, contain the process tree/session, mount only read-only system
# runtime roots that exist, provide private proc/dev/tmp, mount and chdir to an
# explicit workspace, clear the ambient environment, set only PATH/LANG, and
# successfully execute a command without leaking an ambient marker.
#
# Strategy (deterministic, fail closed):
#   1) install ffmpeg and bubblewrap if missing
#   2) execute the production-shape bwrap probe with no permissive fallback
#   3) prefer the runner's pre-installed Docker daemon (docker-compatible API);
#      otherwise start the repo-owned hermetic Podman service (--time=0),
#      mirroring scripts/test/podman-hermetic.sh semantics
#   4) validate the PTEH-V1 contract (socket, /_ping 200, Api-Version >= 1.41)
#   5) persist DOCKER_HOST for subsequent workflow steps (GITHUB_ENV)
set -euo pipefail

# ── 1) host runtime binaries ──
install_packages() {
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update -qq
        sudo apt-get install -y -qq "$@"
    elif command -v zypper >/dev/null 2>&1; then
        sudo zypper --non-interactive install --no-recommends "$@"
    else
        echo "FAIL: no supported package manager found for: $*" >&2
        exit 1
    fi
}

runtime_packages=()
if ! command -v ffmpeg >/dev/null 2>&1; then
    runtime_packages+=("ffmpeg")
fi
if [[ ! -x /usr/bin/bwrap ]]; then
    runtime_packages+=("bubblewrap")
fi
if (( ${#runtime_packages[@]} > 0 )); then
    echo "[ci-test-runtime] installing missing runtime packages: ${runtime_packages[*]}"
    install_packages "${runtime_packages[@]}"
fi
command -v ffmpeg >/dev/null 2>&1 || { echo "FAIL: ffmpeg unavailable after install" >&2; exit 1; }
[[ -x /usr/bin/bwrap ]] || { echo "FAIL: /usr/bin/bwrap unavailable after install" >&2; exit 1; }
echo "[ci-test-runtime] ffmpeg present: $(ffmpeg -version | head -1)"
echo "[ci-test-runtime] bubblewrap present: $(/usr/bin/bwrap --version)"

# ── 2) fail-closed production-shape Bubblewrap probe ──
probe_base="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
[[ -d "$probe_base" ]] || { echo "FAIL: bwrap probe base is not a directory: ${probe_base}" >&2; exit 1; }
probe_base="$(cd "$probe_base" && pwd -P)"
BWRAP_PROBE_ROOT="$(mktemp -d "${probe_base}/media-platform-ci-bwrap-probe.XXXXXX")"
cleanup_bwrap_probe() {
    if [[ -n "${BWRAP_PROBE_ROOT:-}" && -d "$BWRAP_PROBE_ROOT"
            && "$(dirname -- "$BWRAP_PROBE_ROOT")" == "$probe_base"
            && "$(basename -- "$BWRAP_PROBE_ROOT")" == media-platform-ci-bwrap-probe.* ]]; then
        rm -rf -- "$BWRAP_PROBE_ROOT"
    fi
}
trap cleanup_bwrap_probe EXIT

probe_workspace="${BWRAP_PROBE_ROOT}/workspace"
probe_temporary="${probe_workspace}/.sandbox-tmp"
probe_output="${probe_workspace}/.sandbox-output"
mkdir -p -- "$probe_temporary" "$probe_output"
printf 'probe\n' > "${probe_workspace}/input"

BWRAP_PROBE_COMMAND=(
    /usr/bin/bwrap
    --unshare-all
    --die-with-parent
    --new-session
)
for system_root in /usr /bin /lib /lib64; do
    if [[ -e "$system_root" ]]; then
        BWRAP_PROBE_COMMAND+=(--ro-bind "$(readlink -f -- "$system_root")" "$system_root")
    fi
done
BWRAP_PROBE_COMMAND+=(
    --proc /proc
    --dev /dev
    --tmpfs /tmp
    --tmpfs /sandbox-inputs
    --ro-bind "$probe_workspace" /workspace
    --bind "$probe_temporary" /workspace/.sandbox-tmp
    --bind "$probe_output" /workspace/.sandbox-output
    --ro-bind "${probe_workspace}/input" /workspace/input
    --remount-ro /sandbox-inputs
    --chdir /workspace
    --clearenv
    --setenv LANG C
    --setenv PATH /usr/bin:/bin
    /usr/bin/env
)

if ! BWRAP_PROBE_OUTPUT="$(
    env -i MEDIA_PLATFORM_BWRAP_AMBIENT_MARKER=must-not-leak "${BWRAP_PROBE_COMMAND[@]}"
)"; then
    echo "FAIL: bubblewrap production-shape probe failed" >&2
    exit 1
fi
if grep -Fq 'MEDIA_PLATFORM_BWRAP_AMBIENT_MARKER=' <<< "$BWRAP_PROBE_OUTPUT"; then
    echo "FAIL: bubblewrap production-shape probe leaked ambient environment" >&2
    exit 1
fi
EXPECTED_BWRAP_ENV=$'LANG=C\nPATH=/usr/bin:/bin\nPWD=/workspace'
ACTUAL_BWRAP_ENV="$(printf '%s\n' "$BWRAP_PROBE_OUTPUT" | LC_ALL=C sort)"
if [[ "$ACTUAL_BWRAP_ENV" != "$EXPECTED_BWRAP_ENV" ]]; then
    echo "FAIL: bubblewrap production-shape probe environment differs" >&2
    printf 'expected:\n%s\nactual:\n%s\n' "$EXPECTED_BWRAP_ENV" "$ACTUAL_BWRAP_ENV" >&2
    exit 1
fi
echo "[ci-test-runtime] OK: bubblewrap production-shape enforcement probe passed"
cleanup_bwrap_probe
trap - EXIT

# ── 3) container API (deterministic ladder) ──
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
        install_packages podman
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

# ── 4) PTEH-V1 pre-test validation (fail closed) ──
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

# ── 5) persist for subsequent workflow steps ──
if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "DOCKER_HOST=${DOCKER_HOST}" >> "$GITHUB_ENV"
fi
