#!/usr/bin/env python3
"""Mutation-backed contract test for the bounded CI test runtime setup."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SETUP = ROOT / "scripts/ci/setup-test-runtime.sh"

REQUIRED_ONCE = (
    ("fixed-bwrap-path", 'BWRAP_BINARY="/usr/bin/bwrap"'),
    ("conditional-bwrap-install", 'if [[ ! -x "$BWRAP_BINARY" ]]; then'),
    ("bubblewrap-package", "sudo apt-get install -y -qq bubblewrap"),
    ("non-root-preflight", '[[ "$bwrap_preflight_uid" != "0" ]] || fail'),
    ("unshare-all", "--unshare-all"),
    ("die-with-parent", "--die-with-parent"),
    ("new-session", "--new-session"),
    ("clear-environment", "--clearenv"),
    (
        "read-only-generated-input",
        '--ro-bind "$bwrap_preflight_input" /workspace/input',
    ),
    ("isolated-workspace", "--dir /workspace"),
    ("exact-preflight-output", '[[ "$bwrap_preflight_output" == "$bwrap_expected_output" ]] || fail'),
    ("bwrap-binary-evidence", "BUBBLEWRAP_BINARY=%s\\n"),
    ("bwrap-package-evidence", "BUBBLEWRAP_PACKAGE_IDENTITY=%s\\n"),
    ("bwrap-version-evidence", "BUBBLEWRAP_VERSION=%s\\n"),
    ("bwrap-present-evidence", "BUBBLEWRAP_BINARY_PRESENT=YES\\n"),
    ("bwrap-pass-evidence", "BUBBLEWRAP_FUNCTIONAL_PREFLIGHT=PASS\\n"),
    ("bwrap-uid-evidence", "BUBBLEWRAP_PREFLIGHT_UID=%s\\n"),
    (
        "paired-ffmpeg-install",
        "if ! command -v ffmpeg >/dev/null 2>&1 || ! command -v ffprobe >/dev/null 2>&1; then",
    ),
    ("ffmpeg-package", "sudo apt-get install -y -qq ffmpeg"),
    ("dpkg-owned-identity", "dpkg_package_identity_for_binary() {"),
    ("matching-version-token", '[[ "$ffmpeg_version_token" == "$ffprobe_version_token" ]] || fail'),
    ("bounded-major-set", '[[ "$ffmpeg_major" == "6" || "$ffmpeg_major" == "7" ]] || fail'),
    ("x264-build-capability", '[[ "$ffmpeg_configuration" == *"--enable-libx264"* ]] || fail'),
    ("remote-policy", "REMOTE_RUNTIME_IDENTITY_POLICY=BOUNDED_AND_VERIFIED\\n"),
    ("ffmpeg-binary-evidence", "FFMPEG_BINARY=%s\\n"),
    ("ffmpeg-version-evidence", "FFMPEG_VERSION=%s\\n"),
    ("ffmpeg-build-evidence", "FFMPEG_BUILD_EVIDENCE=%s\\n"),
    ("ffmpeg-package-evidence", "FFMPEG_PACKAGE_IDENTITY=%s\\n"),
    ("ffprobe-binary-evidence", "FFPROBE_BINARY=%s\\n"),
    ("ffprobe-version-evidence", "FFPROBE_VERSION=%s\\n"),
    ("ffmpeg-pass-evidence", "FFMPEG_RUNTIME_CONTRACT_RESULT=PASS\\n"),
)


def assert_contract(source: str) -> None:
    missing = [name for name, text in REQUIRED_ONCE if source.count(text) != 1]
    if missing:
        raise AssertionError("missing or duplicated runtime contract clauses: " + ", ".join(missing))

    preflight_start = source.find("# -- bubblewrap functional preflight")
    preflight_end = source.find("# -- FFmpeg/ffprobe bounded runtime identity")
    if preflight_start < 0 or preflight_end <= preflight_start:
        raise AssertionError("bounded bubblewrap preflight section markers are missing or reordered")
    preflight = source[preflight_start:preflight_end]
    if "sudo" in preflight:
        raise AssertionError("bubblewrap functional preflight must run without sudo")
    if 'bwrap_preflight_output="$("$BWRAP_BINARY" "${bwrap_command[@]}")"' not in preflight:
        raise AssertionError("functional preflight does not directly invoke the fixed bwrap binary")

    github_env_writes = [
        line.strip()
        for line in source.splitlines()
        if ">> \"$GITHUB_ENV\"" in line
    ]
    if github_env_writes != ['echo "DOCKER_HOST=${DOCKER_HOST}" >> "$GITHUB_ENV"']:
        raise AssertionError("GITHUB_ENV must persist only the necessary DOCKER_HOST value")


def main() -> None:
    source = SETUP.read_text()
    assert_contract(source)

    for name, text in REQUIRED_ONCE:
        mutated = source.replace(text, "", 1)
        try:
            assert_contract(mutated)
        except AssertionError:
            continue
        raise AssertionError(f"RED mutation passed after removing {name}")

    preflight = source.replace(
        '# -- bubblewrap functional preflight',
        '# -- bubblewrap functional preflight\nsudo true',
        1,
    )
    try:
        assert_contract(preflight)
    except AssertionError:
        pass
    else:
        raise AssertionError("RED mutation passed after adding sudo to the preflight")

    print(
        "SETUP_TEST_RUNTIME_CONTRACT_RED_MATRIX=PASS "
        f"required_clauses={len(REQUIRED_ONCE)} mutations={len(REQUIRED_ONCE) + 1}"
    )


if __name__ == "__main__":
    main()
