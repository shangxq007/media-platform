#!/usr/bin/env python3
"""Mutation-backed contract for the Phase 19 targeted runtime reproof lane."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase19-runtime-targeted.yml"
RUNTIME = ROOT / "scripts/ci/phase19-targeted-runtime-conformance.sh"
VERIFIER = ROOT / "scripts/ci/verify_phase19_targeted_runtime_results.py"
BRANCH = "agent/roadmap22-phase19-runtime-success-path-defect-correction"
NONCE = "media-runtime-targeted-37ce33387e26"
COMMAND = (
    "./gradlew --no-daemon --max-workers=1 :ffmpeg-provider-module:test "
    "--tests '*FfmpegClosedLoopIntegrationTest' --rerun-tasks"
)
METHODS = (
    "real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion",
    "bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority",
    "nonzero_and_cancellation_publish_no_artifact_or_completion",
)


def read_required(path: Path) -> str:
    if not path.is_file():
        raise AssertionError(f"required targeted runtime file is missing: {path.relative_to(ROOT)}")
    return path.read_text()


def assert_workflow(source: str) -> None:
    trigger = (
        "name: Phase19 Targeted Runtime Reproof\n\n"
        "on:\n"
        "  push:\n"
        "    branches:\n"
        f"      - {BRANCH}\n\n"
        "permissions:\n"
        "  contents: read\n"
    )
    if source.count(trigger) != 1:
        raise AssertionError("targeted workflow is not push-only on the exact controlled branch")
    event_block = source.split("on:\n", 1)[1].split("\npermissions:\n", 1)[0]
    if "pull_request" in event_block or "workflow_dispatch" in event_block or "**" in event_block:
        raise AssertionError("targeted workflow trigger was broadened")
    jobs = source.split("\njobs:\n", 1)[1]
    if re.findall(r"^  ([a-z0-9-]+):\s*$", jobs, re.MULTILINE) != ["targeted-runtime-reproof"]:
        raise AssertionError("targeted workflow must contain exactly one targeted-runtime-reproof job")
    required = (
        "timeout-minutes: 90",
        "runs-on:\n      - self-hosted\n      - linux\n      - x64\n      - media-runtime-conformance\n"
        f"      - {NONCE}",
        "EXPECTED_SHA: ${{ github.sha }}",
        "uses: actions/checkout@v4",
        "ref: ${{ github.sha }}",
        "fetch-depth: 0",
        "persist-credentials: false",
        'test -z "$(git status --porcelain)"',
        'checked_sha="$(git rev-parse HEAD)"',
        'test "$checked_sha" = "$GITHUB_SHA"',
        "CHECKED_SHA=%s\\n",
        "run: bash scripts/ci/setup-test-runtime.sh",
        "run: bash scripts/ci/phase19-targeted-runtime-conformance.sh",
    )
    missing = [item for item in required if source.count(item) != 1]
    if missing:
        raise AssertionError("targeted workflow clauses missing or duplicated: " + ", ".join(missing))
    forbidden = (
        "continue-on-error",
        "ubuntu-latest",
        "if: false",
        "if: always()",
        "sudo",
        "--privileged",
        "run-as-root",
        "fallback",
    )
    if any(item.lower() in source.lower() for item in forbidden):
        raise AssertionError("targeted workflow contains skip, hosted, privileged, or fallback behavior")


def assert_runtime(source: str) -> None:
    required = (
        '[[ "${MEDIA_RUNTIME_SETUP_CONFORMANT:-}" == "1" ]] || fail',
        '[[ -n "${EXPECTED_SHA:-}" ]] || fail',
        '[[ -n "${GITHUB_SHA:-}" ]] || fail',
        'head_sha="$(git rev-parse HEAD)"',
        '[[ "$EXPECTED_SHA" == "$head_sha" ]] || fail',
        '[[ "$GITHUB_SHA" == "$head_sha" ]] || fail',
        '[[ -z "$(git status --porcelain)" ]] || fail',
        '[[ "$(id -u)" != "0" ]] || fail',
        '"$HOME/.ssh"',
        '"$HOME/.config/bws"',
        '"$HOME/.hermes"',
        '"$HOME"/.codex*',
        "MEDIA_RUNTIME_BWRAP_IDENTITY",
        "MEDIA_RUNTIME_FFMPEG_IDENTITY",
        "MEDIA_RUNTIME_FFPROBE_IDENTITY",
        '[[ "${MEDIA_RUNTIME_FALLBACK_USED:-}" == "0" ]] || fail',
        '[[ "${MEDIA_RUNTIME_PRIVILEGED_PATH_USED:-}" == "0" ]] || fail',
        'PHASE19_TARGETED_RUNTIME_START_MARKER="build/phase19-targeted-runtime/started.json"',
        COMMAND,
        "python3 scripts/ci/verify_phase19_targeted_runtime_results.py",
        "PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS",
    )
    missing = [item for item in required if item not in source]
    if missing:
        raise AssertionError("targeted runtime clauses missing: " + ", ".join(missing))
    if source.count(COMMAND) != 1:
        raise AssertionError("targeted Gradle command is not exact")
    if source.count("python3 scripts/ci/verify_phase19_targeted_runtime_results.py") != 1:
        raise AssertionError("targeted result verifier invocation is not exact")
    if source.count("PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS") != 1:
        raise AssertionError("targeted runtime PASS marker is not exact")
    forbidden = ("sudo", "--privileged", "continue-on-error")
    if any(item in source for item in forbidden):
        raise AssertionError("targeted runtime contains a privileged or weakening path")
    verifier_pos = source.index("python3 scripts/ci/verify_phase19_targeted_runtime_results.py")
    pass_pos = source.index("PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS")
    if pass_pos <= verifier_pos:
        raise AssertionError("targeted PASS marker precedes result verification")


def assert_verifier(source: str) -> None:
    for method in METHODS:
        if source.count(f'    "{method}",') != 1:
            raise AssertionError(f"targeted verifier method missing or duplicated: {method}")
    required = (
        "xml.etree.ElementTree",
        "TEST-com.example.platform.ffmpeg.FfmpegClosedLoopIntegrationTest.xml",
        'REQUIRED_CLASS = "com.example.platform.ffmpeg.FfmpegClosedLoopIntegrationTest"',
        "PHASE19_TARGETED_RUNTIME_START_MARKER",
        "marker.relative_to(ROOT)",
        "marker_stat.st_mtime_ns",
        "xml_stat.st_mtime_ns < marker_mtime_ns",
        "required_sha(\"EXPECTED_SHA\")",
        "required_sha(\"GITHUB_SHA\")",
        'payload.get("expected_sha") != expected_sha',
        'payload.get("github_sha") != github_sha',
        "declared != observed",
        "declared_tests != 3",
        "declared_failures != 0 or declared_errors != 0 or declared_skipped != 0",
        'method_statuses != ["passed"]',
        "PHASE19_TARGETED_RUNTIME_RESULTS=PASS",
    )
    if any(item not in source for item in required):
        raise AssertionError("targeted verifier lost SHA, freshness, arithmetic, or pass enforcement")
    if source.count("PHASE19_TARGETED_RUNTIME_RESULTS=PASS") != 1:
        raise AssertionError("targeted verifier PASS marker is not exact")


def expect_red(name: str, workflow: str, runtime: str, verifier: str) -> None:
    try:
        assert_workflow(workflow)
        assert_runtime(runtime)
        assert_verifier(verifier)
    except (AssertionError, IndexError):
        return
    raise AssertionError(f"RED mutation passed: {name}")


def main() -> None:
    workflow = read_required(WORKFLOW)
    runtime = read_required(RUNTIME)
    verifier = read_required(VERIFIER)
    assert_workflow(workflow)
    assert_runtime(runtime)
    assert_verifier(verifier)

    pass_line = "python3 scripts/ci/verify_phase19_targeted_runtime_results.py\nprintf 'PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS\\n'"
    reversed_pass_line = "printf 'PHASE19_TARGETED_RUNTIME_CONFORMANCE=PASS\\n'\npython3 scripts/ci/verify_phase19_targeted_runtime_results.py"
    mutations = (
        ("workflow-missing", "", runtime, verifier),
        ("branch-broadened", workflow.replace(BRANCH, "**", 1), runtime, verifier),
        ("pull-request-enabled", workflow.replace("  push:\n", "  pull_request:\n  push:\n", 1), runtime, verifier),
        ("nonce-removed", workflow.replace(f"      - {NONCE}\n", "", 1), runtime, verifier),
        ("generic-label-removed", workflow.replace("      - media-runtime-conformance\n", "", 1), runtime, verifier),
        ("sha-guard-removed", workflow.replace('          checked_sha="$(git rev-parse HEAD)"\n', "", 1), runtime, verifier),
        ("clean-guard-removed", workflow.replace('          test -z "$(git status --porcelain)"\n', "", 1), runtime, verifier),
        ("checkout-shallow", workflow.replace("fetch-depth: 0", "fetch-depth: 1", 1), runtime, verifier),
        ("checkout-credentials", workflow.replace("persist-credentials: false", "persist-credentials: true", 1), runtime, verifier),
        ("setup-removed", workflow.replace("      - name: Set up authoritative test runtime\n        run: bash scripts/ci/setup-test-runtime.sh\n", "", 1), runtime, verifier),
        ("targeted-script-removed", workflow.replace("      - name: Run Phase 19 targeted runtime reproof\n        run: bash scripts/ci/phase19-targeted-runtime-conformance.sh\n", "", 1), runtime, verifier),
        ("continue-on-error", workflow.replace("        run: bash scripts/ci/phase19-targeted-runtime-conformance.sh", "        continue-on-error: true\n        run: bash scripts/ci/phase19-targeted-runtime-conformance.sh", 1), runtime, verifier),
        ("skip-if-false", workflow.replace("    timeout-minutes: 90", "    if: false\n    timeout-minutes: 90", 1), runtime, verifier),
        ("full-suite-substituted", workflow, runtime.replace(COMMAND, "./gradlew --no-daemon --max-workers=1 test --rerun-tasks", 1), verifier),
        ("one-method-dropped", workflow, runtime, verifier.replace(f'    "{METHODS[0]}",\n', "", 1)),
        ("skip-check-weakened", workflow, runtime, verifier.replace("declared_skipped != 0", "declared_skipped < 0", 1)),
        ("freshness-dropped", workflow, runtime, verifier.replace("    if xml_stat.st_mtime_ns < marker_mtime_ns:\n        fail(\"FfmpegClosedLoopIntegrationTest JUnit XML predates the targeted marker\")\n", "", 1)),
        ("pass-before-verifier", workflow, runtime.replace(pass_line, reversed_pass_line, 1), verifier),
        ("fallback-token", workflow + "\n# fallback\n", runtime, verifier),
        ("root-token", workflow + "\n# run-as-root\n", runtime, verifier),
        ("privileged-token", workflow + "\n# --privileged\n", runtime, verifier),
    )
    for mutation in mutations:
        expect_red(*mutation)

    print(
        "PHASE19_TARGETED_RUNTIME_CONFORMANCE_CONTRACT_RED_MATRIX=PASS "
        f"mutations={len(mutations)} methods={len(METHODS)}"
    )


if __name__ == "__main__":
    main()
