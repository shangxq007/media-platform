#!/usr/bin/env bash
# PFIRR1-B3: deterministic test-runtime provisioning for GitHub-hosted runners.
#
# Provides the docker-compatible container API contract required by
# PostgresTestContainerSupport and HermeticPodmanServiceContractTest (PTEH-V1):
#   - a unix-socket Docker-compatible API at DOCKER_HOST
#   - /_ping responds HTTP 200 with Api-Version >= 1.41
#   - the service survives the historical idle window (no 5s churn; --time=0)
# plus the fail-closed bubblewrap and FFmpeg/ffprobe capabilities required by
# sandboxed provider and render-boundary media tests.
#
# Strategy (deterministic, fail closed):
#   1) provision and functionally validate /usr/bin/bwrap as the non-root runner
#   2) provision and validate bounded package/build identity for ffmpeg/ffprobe
#   3) prefer the runner's pre-installed Docker daemon (docker-compatible API);
#      otherwise start the repo-owned hermetic Podman service (--time=0),
#      mirroring scripts/test/podman-hermetic.sh semantics
#   4) validate the PTEH-V1 contract (socket, /_ping 200, Api-Version >= 1.41)
#   5) persist DOCKER_HOST for subsequent workflow steps (GITHUB_ENV)
set -euo pipefail

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

dpkg_package_identity_for_binary() {
    local binary="$1"
    local owner_record owner_suffix owner package_record

    command -v dpkg-query >/dev/null 2>&1 || fail "dpkg-query unavailable for runtime identity"
    owner_record="$(dpkg-query --search "$binary" 2>/dev/null)" \
        || fail "no dpkg owner for runtime binary: ${binary}"
    [[ "$owner_record" != *$'\n'* ]] \
        || fail "multiple dpkg owners for runtime binary: ${binary}"
    owner_suffix=": ${binary}"
    [[ "$owner_record" == *"$owner_suffix" ]] \
        || fail "unexpected dpkg owner record for runtime binary: ${binary}"
    owner="${owner_record%"$owner_suffix"}"
    [[ -n "$owner" ]] || fail "empty dpkg package owner for runtime binary: ${binary}"

    package_record="$(dpkg-query --show --showformat='${db:Status-Abbrev}|${binary:Package}=${Version}' "$owner" 2>/dev/null)" \
        || fail "dpkg package identity unavailable for runtime binary: ${binary}"
    [[ "$package_record" == 'ii |'* ]] \
        || fail "dpkg package is not exactly installed for runtime binary: ${binary}"
    printf '%s\n' "${package_record#'ii |'}"
}

# ── bubblewrap package and exact binary path ──
BWRAP_BINARY="/usr/bin/bwrap"
if [[ ! -x "$BWRAP_BINARY" ]]; then
    echo "[ci-test-runtime] /usr/bin/bwrap unavailable; installing bubblewrap"
    sudo apt-get update -qq
    sudo apt-get install -y -qq bubblewrap
fi
[[ -x "$BWRAP_BINARY" ]] || fail "/usr/bin/bwrap unavailable after install"
bubblewrap_package_identity="$(dpkg_package_identity_for_binary "$BWRAP_BINARY")"
bubblewrap_version="$("$BWRAP_BINARY" --version 2>&1)" \
    || fail "/usr/bin/bwrap version query failed"
[[ -n "$bubblewrap_version" && "$bubblewrap_version" != *$'\n'* ]] \
    || fail "/usr/bin/bwrap returned an invalid version identity"

# -- bubblewrap functional preflight
bwrap_preflight_uid="$(id -u)"
[[ "$bwrap_preflight_uid" != "0" ]] || fail "bubblewrap preflight must run as the non-root runner"
bwrap_preflight_root="$(mktemp -d)"
trap 'rm -rf -- "$bwrap_preflight_root"' EXIT
bwrap_preflight_input="${bwrap_preflight_root}/generated-marker-input"
bwrap_marker="media-platform-bwrap-functional-preflight-v1"
printf '%s\n' "$bwrap_marker" > "$bwrap_preflight_input"

bwrap_command=(--unshare-all --die-with-parent --new-session --clearenv)
for system_root in /usr /bin /lib /lib64; do
    if [[ -e "$system_root" ]]; then
        bwrap_command+=(--ro-bind "$system_root" "$system_root")
    fi
done
bwrap_command+=(
    --proc /proc
    --dev /dev
    --tmpfs /tmp
    --dir /workspace
    --ro-bind "$bwrap_preflight_input" /workspace/input
    --chdir /workspace
    --setenv PATH /usr/bin:/bin
    /bin/sh -c 'IFS= read -r marker < /workspace/input; printf "marker=%s\nworkspace=%s\nuid=%s\n" "$marker" "$PWD" "$(id -u)"'
)
bwrap_expected_output="$(printf 'marker=%s\nworkspace=/workspace\nuid=%s\n' "$bwrap_marker" "$bwrap_preflight_uid")"
bwrap_preflight_output="$("$BWRAP_BINARY" "${bwrap_command[@]}")" \
    || fail "/usr/bin/bwrap functional preflight invocation failed"
[[ "$bwrap_preflight_output" == "$bwrap_expected_output" ]] || fail "bubblewrap functional preflight output differed"

printf 'BUBBLEWRAP_BINARY=%s\n' "$BWRAP_BINARY"
printf 'BUBBLEWRAP_PACKAGE_IDENTITY=%s\n' "$bubblewrap_package_identity"
printf 'BUBBLEWRAP_VERSION=%s\n' "$bubblewrap_version"
printf 'BUBBLEWRAP_BINARY_PRESENT=YES\n'
printf 'BUBBLEWRAP_FUNCTIONAL_PREFLIGHT=PASS\n'
printf 'BUBBLEWRAP_PREFLIGHT_UID=%s\n' "$bwrap_preflight_uid"

# -- FFmpeg/ffprobe bounded runtime identity
if ! command -v ffmpeg >/dev/null 2>&1 || ! command -v ffprobe >/dev/null 2>&1; then
    echo "[ci-test-runtime] ffmpeg or ffprobe missing; installing ffmpeg package"
    sudo apt-get update -qq
    sudo apt-get install -y -qq ffmpeg
fi
command -v ffmpeg >/dev/null 2>&1 || fail "ffmpeg unavailable after install"
command -v ffprobe >/dev/null 2>&1 || fail "ffprobe unavailable after install"

FFMPEG_BINARY="$(readlink -f "$(command -v ffmpeg)")"
FFPROBE_BINARY="$(readlink -f "$(command -v ffprobe)")"
[[ "$FFMPEG_BINARY" == /* && -x "$FFMPEG_BINARY" ]] || fail "ffmpeg did not resolve to an absolute executable"
[[ "$FFPROBE_BINARY" == /* && -x "$FFPROBE_BINARY" ]] || fail "ffprobe did not resolve to an absolute executable"

ffmpeg_package_identity="$(dpkg_package_identity_for_binary "$FFMPEG_BINARY")"
ffprobe_package_identity="$(dpkg_package_identity_for_binary "$FFPROBE_BINARY")"
[[ "$ffmpeg_package_identity" == "$ffprobe_package_identity" ]] \
    || fail "ffmpeg and ffprobe resolve to different dpkg package identities"

ffmpeg_version_output="$("$FFMPEG_BINARY" -version 2>&1)" || fail "ffmpeg version query failed"
ffprobe_version_output="$("$FFPROBE_BINARY" -version 2>&1)" || fail "ffprobe version query failed"
ffmpeg_version_line="$(printf '%s\n' "$ffmpeg_version_output" | sed -n '1p')"
ffprobe_version_line="$(printf '%s\n' "$ffprobe_version_output" | sed -n '1p')"
ffmpeg_configuration="$(printf '%s\n' "$ffmpeg_version_output" | sed -n '/^configuration:/p')"
[[ -n "$ffmpeg_configuration" && "$ffmpeg_configuration" != *$'\n'* ]] \
    || fail "ffmpeg configuration evidence is missing or ambiguous"

read -r ffmpeg_name ffmpeg_version_word ffmpeg_version_token _ <<< "$ffmpeg_version_line"
read -r ffprobe_name ffprobe_version_word ffprobe_version_token _ <<< "$ffprobe_version_line"
[[ "$ffmpeg_name" == "ffmpeg" && "$ffmpeg_version_word" == "version" && -n "$ffmpeg_version_token" ]] \
    || fail "ffmpeg first version line is malformed"
[[ "$ffprobe_name" == "ffprobe" && "$ffprobe_version_word" == "version" && -n "$ffprobe_version_token" ]] \
    || fail "ffprobe first version line is malformed"
[[ "$ffmpeg_version_token" == "$ffprobe_version_token" ]] || fail "ffmpeg and ffprobe version tokens differ"
ffmpeg_major="${ffmpeg_version_token%%.*}"
[[ "$ffmpeg_major" == "6" || "$ffmpeg_major" == "7" ]] || fail "ffmpeg major is outside the evidence-backed set {6,7}"
[[ "$ffmpeg_configuration" == *"--enable-libx264"* ]] || fail "ffmpeg build lacks --enable-libx264"

printf 'REMOTE_RUNTIME_IDENTITY_POLICY=BOUNDED_AND_VERIFIED\n'
printf 'FFMPEG_BINARY=%s\n' "$FFMPEG_BINARY"
printf 'FFMPEG_VERSION=%s\n' "$ffmpeg_version_line"
printf 'FFMPEG_BUILD_EVIDENCE=%s\n' "$ffmpeg_configuration"
printf 'FFMPEG_PACKAGE_IDENTITY=%s\n' "$ffmpeg_package_identity"
printf 'FFPROBE_BINARY=%s\n' "$FFPROBE_BINARY"
printf 'FFPROBE_VERSION=%s\n' "$ffprobe_version_line"
printf 'FFMPEG_RUNTIME_CONTRACT_RESULT=PASS\n'

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
