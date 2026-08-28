#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

require_bounded_value() {
    local name="$1"
    local value="${!name:-}"
    [[ -n "$value" && ${#value} -le 512 && "$value" != *$'\n'* && "$value" != *$'\r'* ]] \
        || fail "${name} is missing or unbounded"
}

[[ "${MEDIA_RUNTIME_SETUP_CONFORMANT:-}" == "1" ]] || fail "authoritative runtime setup sentinel is absent"
[[ -n "${EXPECTED_SHA:-}" ]] || fail "EXPECTED_SHA is empty"
[[ -n "${GITHUB_SHA:-}" ]] || fail "GITHUB_SHA is empty"
head_sha="$(git rev-parse HEAD)"
[[ "$EXPECTED_SHA" == "$head_sha" ]] || fail "EXPECTED_SHA does not equal HEAD"
[[ "$GITHUB_SHA" == "$head_sha" ]] || fail "GITHUB_SHA does not equal HEAD"
[[ -z "$(git status --porcelain)" ]] || fail "runtime checkout is not clean"
[[ "$(id -u)" != "0" ]] || fail "runtime conformance must run as a non-root identity"

require_bounded_value HOME
require_bounded_value GITHUB_WORKSPACE
workspace_root="$(git rev-parse --show-toplevel)"
[[ "$GITHUB_WORKSPACE" == "$workspace_root" ]] || fail "GITHUB_WORKSPACE does not equal the checkout root"

uname_identity="$(uname -srm)"
require_bounded_value uname_identity
os_id="unavailable"
os_version_id="unavailable"
if [[ -r /etc/os-release ]]; then
    os_id="$(sed -n 's/^ID=//p' /etc/os-release | head -n 1 | tr -d '"')"
    os_version_id="$(sed -n 's/^VERSION_ID=//p' /etc/os-release | head -n 1 | tr -d '"')"
fi
require_bounded_value os_id
require_bounded_value os_version_id
printf 'RUNNER_UID=%s\n' "$(id -u)"
printf 'RUNNER_UNAME=%s\n' "$uname_identity"
printf 'RUNNER_OS_ID=%s\n' "$os_id"
printf 'RUNNER_OS_VERSION_ID=%s\n' "$os_version_id"
printf 'RUNNER_HOME=%s\n' "$HOME"
printf 'RUNNER_WORKSPACE=%s\n' "$workspace_root"
printf 'RUNTIME_CHECKED_SHA=%s\n' "$head_sha"

for forbidden_home_path in "$HOME/.ssh" "$HOME/.config/bws" "$HOME/.hermes"; do
    [[ ! -e "$forbidden_home_path" ]] || fail "runner HOME contains a prohibited control directory"
done
shopt -s nullglob
codex_home_paths=("$HOME"/.codex*)
shopt -u nullglob
(( ${#codex_home_paths[@]} == 0 )) || fail "runner HOME contains a prohibited Codex path"
printf 'RUNNER_HOME_CONTROL_PATH_ISOLATION=PASS\n'

require_bounded_value MEDIA_RUNTIME_BWRAP_IDENTITY
require_bounded_value MEDIA_RUNTIME_FFMPEG_IDENTITY
require_bounded_value MEDIA_RUNTIME_FFPROBE_IDENTITY
[[ "${MEDIA_RUNTIME_FALLBACK_USED:-}" == "0" ]] || fail "runtime setup used a fallback path"
[[ "${MEDIA_RUNTIME_PRIVILEGED_PATH_USED:-}" == "0" ]] || fail "runtime setup used a privileged path"
printf 'RUNTIME_BWRAP_IDENTITY=%s\n' "$MEDIA_RUNTIME_BWRAP_IDENTITY"
printf 'RUNTIME_FFMPEG_IDENTITY=%s\n' "$MEDIA_RUNTIME_FFMPEG_IDENTITY"
printf 'RUNTIME_FFPROBE_IDENTITY=%s\n' "$MEDIA_RUNTIME_FFPROBE_IDENTITY"

PHASE19_RUNTIME_START_MARKER="build/phase19-runtime-conformance/started.json"
export PHASE19_RUNTIME_START_MARKER
mkdir -p "$(dirname "$PHASE19_RUNTIME_START_MARKER")"
python3 - "$PHASE19_RUNTIME_START_MARKER" <<'PY'
import json
import os
import sys
import time
from pathlib import Path

marker = Path(sys.argv[1])
marker.write_text(json.dumps({
    "expected_sha": os.environ["EXPECTED_SHA"],
    "github_sha": os.environ["GITHUB_SHA"],
    "started_at_ns": time.time_ns(),
}, sort_keys=True) + "\n")
PY

./gradlew --no-daemon --max-workers=1 test --rerun-tasks

./gradlew --no-daemon --max-workers=1 \
    :platform-distribution:stageModularDistribution \
    :platform-distribution:modularDistribution \
    :platform-distribution:allInOneJar \
    :platform-distribution:verifyDualDistributionPluginDigest

python3 scripts/phase19-clean-forward-guards.py
python3 scripts/test_phase19_clean_forward_guards.py
python3 scripts/ci/verify_phase19_runtime_conformance_results.py

expected_plugin_digest="df496276e7a087431d9e5ded07163d92d2ccacaede2c0250fb9f8d9ea0319c30"
producer_plugin="ffmpeg-provider-module/build/libs/ffmpeg-provider-plugin-1.0.0.jar"
modular_plugin="platform-distribution/build/distributions/modular/plugins/ffmpeg-provider-plugin-1.0.0.jar"
all_in_one="platform-distribution/build/libs/media-platform-all-in-one.jar"
embedded_entry="embedded-plugins/ffmpeg-provider-plugin-1.0.0.jar"
for artifact in "$producer_plugin" "$modular_plugin" "$all_in_one"; do
    [[ -f "$artifact" ]] || fail "required distribution artifact is missing: ${artifact}"
done
producer_digest="$(sha256sum -- "$producer_plugin")"
producer_digest="${producer_digest%% *}"
modular_digest="$(sha256sum -- "$modular_plugin")"
modular_digest="${modular_digest%% *}"
embedded_digest="$(unzip -p "$all_in_one" "$embedded_entry" | sha256sum)"
embedded_digest="${embedded_digest%% *}"
[[ "$producer_digest" == "$expected_plugin_digest" ]] || fail "producer plugin digest differs from the expected digest"
[[ "$modular_digest" == "$expected_plugin_digest" ]] || fail "modular plugin digest differs from the expected digest"
[[ "$embedded_digest" == "$expected_plugin_digest" ]] || fail "embedded plugin digest differs from the expected digest"
printf 'PHASE19_PLUGIN_PRODUCER_SHA256=%s\n' "$producer_digest"
printf 'PHASE19_PLUGIN_MODULAR_SHA256=%s\n' "$modular_digest"
printf 'PHASE19_PLUGIN_EMBEDDED_SHA256=%s\n' "$embedded_digest"

printf 'PHASE19_RUNTIME_SECURITY_CONFORMANCE=PASS\n'
printf 'PHASE19_PLUGIN_DISTRIBUTION_CONFORMANCE=PASS\n'
printf 'PHASE19_ARTIFACT_CANCELLATION_EQUIVALENCE=PASS\n'
