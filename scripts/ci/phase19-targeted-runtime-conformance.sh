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

for forbidden_home_path in "$HOME/.ssh" "$HOME/.config/bws" "$HOME/.hermes"; do
    [[ ! -e "$forbidden_home_path" ]] || fail "runner HOME contains a prohibited control directory"
done
shopt -s nullglob
codex_home_paths=("$HOME"/.codex*)
shopt -u nullglob
(( ${#codex_home_paths[@]} == 0 )) || fail "runner HOME contains a prohibited Codex path"

require_bounded_value MEDIA_RUNTIME_BWRAP_IDENTITY
require_bounded_value MEDIA_RUNTIME_FFMPEG_IDENTITY
require_bounded_value MEDIA_RUNTIME_FFPROBE_IDENTITY
[[ "${MEDIA_RUNTIME_FALLBACK_USED:-}" == "0" ]] || fail "runtime setup used a fallback path"
[[ "${MEDIA_RUNTIME_PRIVILEGED_PATH_USED:-}" == "0" ]] || fail "runtime setup used a privileged path"

printf 'TARGETED_RUNTIME_CHECKED_SHA=%s\n' "$head_sha"
printf 'TARGETED_RUNTIME_HOME=%s\n' "$HOME"
printf 'TARGETED_RUNTIME_WORKSPACE=%s\n' "$workspace_root"
printf 'TARGETED_RUNTIME_BWRAP_IDENTITY=%s\n' "$MEDIA_RUNTIME_BWRAP_IDENTITY"
printf 'TARGETED_RUNTIME_FFMPEG_IDENTITY=%s\n' "$MEDIA_RUNTIME_FFMPEG_IDENTITY"
printf 'TARGETED_RUNTIME_FFPROBE_IDENTITY=%s\n' "$MEDIA_RUNTIME_FFPROBE_IDENTITY"
printf 'TARGETED_RUNTIME_HOME_CONTROL_PATH_ISOLATION=PASS\n'

PHASE19_TARGETED_RUNTIME_START_MARKER="build/phase19-targeted-runtime/started.json"
export PHASE19_TARGETED_RUNTIME_START_MARKER
mkdir -p "$(dirname "$PHASE19_TARGETED_RUNTIME_START_MARKER")"
python3 - "$PHASE19_TARGETED_RUNTIME_START_MARKER" <<'PY'
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

./gradlew --no-daemon --max-workers=1 :ffmpeg-provider-module:test --tests '*FfmpegClosedLoopIntegrationTest' --rerun-tasks

python3 scripts/ci/verify_phase19_targeted_runtime_results.py
printf 'PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS\n'
