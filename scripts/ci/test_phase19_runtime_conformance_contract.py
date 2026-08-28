#!/usr/bin/env python3
"""Mutation-backed contract for the Phase 19 dedicated runtime capability lane."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase19-runtime-conformance.yml"
RUNTIME = ROOT / "scripts/ci/phase19-runtime-conformance.sh"
VERIFIER = ROOT / "scripts/ci/verify_phase19_runtime_conformance_results.py"
BRANCH = "agent/roadmap22-phase19-runtime-success-path-defect-correction"
NONCE = "media-runtime-full-18e2193c23b1"
DIGEST = "df496276e7a087431d9e5ded07163d92d2ccacaede2c0250fb9f8d9ea0319c30"
METHODS = (
    "nonzero_and_cancellation_publish_no_artifact_or_completion",
    "bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority",
    "real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion",
)
MODULES = (
    "ffmpeg-provider-module",
    "sandbox-isolation-module",
    "worker-fabric-module",
    "provider-plugin-runtime-module",
    "artifact-module",
    "platform-distribution",
)


def read_required(path: Path) -> str:
    if not path.is_file():
        raise AssertionError(f"required runtime conformance file is missing: {path.relative_to(ROOT)}")
    return path.read_text()


def assert_workflow(source: str) -> None:
    trigger = (
        "name: Phase19 Runtime Conformance\n\n"
        "on:\n"
        "  push:\n"
        "    branches:\n"
        f"      - {BRANCH}\n\n"
        "permissions:\n"
        "  contents: read\n"
    )
    if source.count(trigger) != 1:
        raise AssertionError("workflow is not push-only on the exact controlled branch")
    event_block = source.split("on:\n", 1)[1].split("\npermissions:\n", 1)[0]
    if "pull_request" in event_block or "workflow_dispatch" in event_block or "**" in event_block:
        raise AssertionError("runtime workflow trigger was broadened")
    jobs = source.split("\njobs:\n", 1)[1]
    if re.findall(r"^  ([a-z0-9-]+):\s*$", jobs, re.MULTILINE) != ["runtime-conformance"]:
        raise AssertionError("runtime workflow must contain exactly one runtime-conformance job")
    required = (
        "timeout-minutes: 180",
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
        "run: bash scripts/ci/phase19-runtime-conformance.sh",
    )
    missing = [item for item in required if source.count(item) != 1]
    if missing:
        raise AssertionError("workflow clauses missing or duplicated: " + ", ".join(missing))
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
        raise AssertionError("workflow contains skip, hosted, privileged, or fallback behavior")


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
        "PHASE19_RUNTIME_START_MARKER",
        "./gradlew --no-daemon --max-workers=1 test --rerun-tasks",
        ":platform-distribution:stageModularDistribution",
        ":platform-distribution:modularDistribution",
        ":platform-distribution:allInOneJar",
        ":platform-distribution:verifyDualDistributionPluginDigest",
        "python3 scripts/phase19-clean-forward-guards.py",
        "python3 scripts/test_phase19_clean_forward_guards.py",
        "python3 scripts/ci/verify_phase19_runtime_conformance_results.py",
        "unzip -p",
        DIGEST,
        "PHASE19_RUNTIME_SECURITY_CONFORMANCE=PASS",
        "PHASE19_PLUGIN_DISTRIBUTION_CONFORMANCE=PASS",
        "PHASE19_ARTIFACT_CANCELLATION_EQUIVALENCE=PASS",
    )
    missing = [item for item in required if item not in source]
    if missing:
        raise AssertionError("runtime clauses missing: " + ", ".join(missing))
    if source.count("./gradlew --no-daemon --max-workers=1 test --rerun-tasks") != 1:
        raise AssertionError("full authoritative test suite command is not exact")
    if "sudo" in source or "--privileged" in source or "continue-on-error" in source:
        raise AssertionError("runtime conformance contains a privileged or weakening path")
    verifier_pos = source.index("python3 scripts/ci/verify_phase19_runtime_conformance_results.py")
    digest_pos = source.index(DIGEST)
    first_pass = min(source.index(marker) for marker in (
        "PHASE19_RUNTIME_SECURITY_CONFORMANCE=PASS",
        "PHASE19_PLUGIN_DISTRIBUTION_CONFORMANCE=PASS",
        "PHASE19_ARTIFACT_CANCELLATION_EQUIVALENCE=PASS",
    ))
    if first_pass <= verifier_pos or first_pass <= digest_pos:
        raise AssertionError("PASS markers precede verifier or digest enforcement")


def assert_verifier(source: str) -> None:
    for item in METHODS + MODULES:
        if source.count(f'"{item}"') != 1:
            raise AssertionError(f"verifier manifest item missing or duplicated: {item}")
    required = (
        "xml.etree.ElementTree",
        "PHASE19_RUNTIME_START_MARKER",
        "st_mtime_ns",
        'root.glob("build/test-results/test/TEST-*.xml")',
        'failures != 0 or errors != 0',
        'required_skipped != 0',
        'required_executed != len(REQUIRED_METHODS)',
        "PHASE19_RUNTIME_CONFORMANCE_RESULTS=PASS",
    )
    if any(item not in source for item in required):
        raise AssertionError("result verifier lost freshness, XML, failure, or execution enforcement")


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

    mutations = (
        ("workflow-missing", "", runtime, verifier),
        ("pull-request-enabled", workflow.replace("  push:\n", "  pull_request:\n  push:\n", 1), runtime, verifier),
        ("branch-broadened", workflow.replace(BRANCH, "**", 1), runtime, verifier),
        ("nonce-removed", workflow.replace(f"      - {NONCE}\n", "", 1), runtime, verifier),
        ("generic-label-removed", workflow.replace("      - media-runtime-conformance\n", "", 1), runtime, verifier),
        ("sha-guard-removed", workflow.replace('        checked_sha="$(git rev-parse HEAD)"\n', "", 1), runtime, verifier),
        ("clean-guard-removed", workflow.replace('        test -z "$(git status --porcelain)"\n', "", 1), runtime, verifier),
        ("setup-step-removed", workflow.replace("      - name: Set up authoritative test runtime\n        run: bash scripts/ci/setup-test-runtime.sh\n", "", 1), runtime, verifier),
        ("runtime-step-removed", workflow.replace("      - name: Run Phase 19 runtime conformance\n        run: bash scripts/ci/phase19-runtime-conformance.sh\n", "", 1), runtime, verifier),
        ("continue-on-error", workflow.replace("        run: bash scripts/ci/phase19-runtime-conformance.sh", "        continue-on-error: true\n        run: bash scripts/ci/phase19-runtime-conformance.sh", 1), runtime, verifier),
        ("checkout-shallow", workflow.replace("fetch-depth: 0", "fetch-depth: 1", 1), runtime, verifier),
        ("checkout-credentials", workflow.replace("persist-credentials: false", "persist-credentials: true", 1), runtime, verifier),
        ("root-token", workflow + "\n# run-as-root\n", runtime, verifier),
        ("privileged-token", workflow + "\n# --privileged\n", runtime, verifier),
        ("fallback-token", workflow + "\n# fallback\n", runtime, verifier),
        ("missing-runner-skips", workflow.replace("    timeout-minutes: 180", "    if: false\n    timeout-minutes: 180", 1), runtime, verifier),
        ("setup-sentinel-dropped", workflow, runtime.replace("MEDIA_RUNTIME_SETUP_CONFORMANT", "MEDIA_RUNTIME_SETUP_IGNORED", 1), verifier),
        ("full-test-dropped", workflow, runtime.replace("./gradlew --no-daemon --max-workers=1 test --rerun-tasks", ":", 1), verifier),
        ("verifier-call-dropped", workflow, runtime.replace("python3 scripts/ci/verify_phase19_runtime_conformance_results.py", ":", 1), verifier),
        ("digest-check-dropped", workflow, runtime.replace(DIGEST, "0" * 64, 1), verifier),
        ("verifier-method-dropped", workflow, runtime, verifier.replace(f'    "{METHODS[0]}",\n', "", 1)),
        ("verifier-allows-skipped", workflow, runtime, verifier.replace("required_skipped != 0", "required_skipped < 0", 1)),
    )
    for mutation in mutations:
        expect_red(*mutation)

    print(
        "PHASE19_RUNTIME_CONFORMANCE_CONTRACT_RED_MATRIX=PASS "
        f"workflow_runtime_verifier_mutations={len(mutations)} methods={len(METHODS)} modules={len(MODULES)}"
    )


if __name__ == "__main__":
    main()
