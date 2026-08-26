#!/usr/bin/env python3
"""Fail-closed exact-SHA JUnit guard for Phase 17 sandbox conformance."""
from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SCHEMA = [
    "TEST_ID",
    "MODULE",
    "CLASS",
    "METHOD_OR_SCOPE",
    "REQUIRED_CAPABILITY",
    "AUTHORITY_REASON",
]
EXPECTED = {
    (
        "P17-SC-001",
        "sandbox-isolation-module",
        "com.example.platform.sandbox.BubblewrapSandboxProcessLauncherIntegrationTest",
        "real_bubblewrap_enforces_the_advertised_host_binary_boundaries",
    ),
    (
        "P17-SC-002",
        "sandbox-isolation-module",
        "com.example.platform.sandbox.ContainerSandboxProcessLauncherIntegrationTest",
        "rootless_container_mechanically_enforces_the_advertised_boundaries",
    ),
    (
        "P17-SC-003",
        "platform-app",
        "com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProviderTest",
        "testValidVideoIfFFprobeAvailable",
    ),
    (
        "P17-SC-004",
        "platform-app",
        "com.example.platform.ingest.preflight.IngestMetadataMergerTest",
        "testFfprobeForVideo",
    ),
}
EXPECTED_RESULT_ROOTS = {
    (ROOT / "sandbox-isolation-module/build/test-results/phase17SandboxConformanceTest").resolve(),
    (ROOT / "platform-app/build/test-results/phase17SandboxConformanceTest").resolve(),
}
EXPECTED_MARKER = (ROOT / "build/phase17-sandbox-conformance/started.json").resolve()
SHA = re.compile(r"[0-9a-f]{40}")
JUNIT_METHOD = re.compile(r"(?P<method>[A-Za-z_$][A-Za-z0-9_$]*)\([^()]*(?:\[.*\])?\)")


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_manifest(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        fail(f"missing conformance manifest: {path}")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != SCHEMA:
            fail(f"manifest schema differs: {reader.fieldnames}")
        rows = list(reader)
    if len(rows) != len(EXPECTED):
        fail(f"manifest entry count differs: {len(rows)}")
    for number, row in enumerate(rows, 1):
        for field in SCHEMA:
            value = row.get(field, "").strip()
            if not value:
                fail(f"manifest row {number} has empty {field}")
            if "..." in value or "*" in value or "?" in value:
                fail(f"manifest row {number} has a vague {field}")
    ids = [row["TEST_ID"] for row in rows]
    identities = [(row["CLASS"], row["METHOD_OR_SCOPE"]) for row in rows]
    if len(set(ids)) != len(ids):
        fail("manifest has duplicate TEST_ID values")
    if len(set(identities)) != len(identities):
        fail("manifest has duplicate test identities")
    actual = {
        (row["TEST_ID"], row["MODULE"], row["CLASS"], row["METHOD_OR_SCOPE"])
        for row in rows
    }
    if actual != EXPECTED:
        fail(f"manifest identities differ: {sorted(actual)}")
    return rows


def require_exact_sha(expected_sha: str) -> None:
    if not SHA.fullmatch(expected_sha):
        fail("expected SHA is not an exact lowercase 40-hex Git object name")
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True,
        capture_output=True, check=False,
    )
    if result.returncode != 0:
        fail("git rev-parse HEAD failed")
    actual_sha = result.stdout.strip()
    if actual_sha != expected_sha:
        fail(f"source SHA differs: expected={expected_sha} actual={actual_sha}")


def read_freshness_marker(path: Path, expected_sha: str) -> int:
    if path.resolve() != EXPECTED_MARKER or not path.is_file():
        fail(f"freshness marker is absent or non-authoritative: {path}")
    try:
        marker = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"freshness marker is invalid: {error}")
    if set(marker) != {"sha", "started_at_ns"}:
        fail("freshness marker schema differs")
    if marker["sha"] != expected_sha:
        fail("freshness marker SHA differs")
    started_at_ns = marker["started_at_ns"]
    if not isinstance(started_at_ns, int) or started_at_ns <= 0:
        fail("freshness marker started_at_ns is invalid")
    if started_at_ns > time.time_ns():
        fail("freshness marker is from the future")
    return started_at_ns


def junit_method_name(raw_name: str) -> str:
    match = JUNIT_METHOD.fullmatch(raw_name)
    if not match:
        fail(f"JUnit testcase name is not a precise method invocation: {raw_name}")
    return match.group("method")


def parse_results(
    roots: list[Path], rows: list[dict[str, str]], started_at_ns: int
) -> tuple[int, int, int, int]:
    resolved_roots = {root.resolve() for root in roots}
    if resolved_roots != EXPECTED_RESULT_ROOTS or len(roots) != len(resolved_roots):
        fail(f"result roots differ: {sorted(str(root) for root in resolved_roots)}")
    expected_files = {
        (ROOT / row["MODULE"] / "build/test-results/phase17SandboxConformanceTest"
         / f'TEST-{row["CLASS"]}.xml').resolve()
        for row in rows
    }
    result_files: set[Path] = set()
    for root in resolved_roots:
        if not root.is_dir():
            fail(f"expected JUnit result root is absent: {root}")
        result_files.update(path.resolve() for path in root.glob("TEST-*.xml"))
    if result_files != expected_files:
        fail(
            "JUnit result files differ: "
            f"expected={sorted(str(path) for path in expected_files)} "
            f"actual={sorted(str(path) for path in result_files)}"
        )

    identities: Counter[tuple[str, str]] = Counter()
    selected_total = skipped = failures = errors = 0
    for path in sorted(result_files):
        if not path.is_file() or path.stat().st_mtime_ns < started_at_ns:
            fail(f"JUnit result is absent or stale: {path}")
        try:
            suite = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            fail(f"JUnit XML is invalid at {path}: {error}")
        if suite.tag != "testsuite":
            fail(f"JUnit XML root is not testsuite: {path}")
        cases = suite.findall("testcase")
        selected_total += len(cases)
        suite_skipped = suite_failures = suite_errors = 0
        for case in cases:
            class_name = case.get("classname", "")
            method_name = junit_method_name(case.get("name", ""))
            identities[(class_name, method_name)] += 1
            case_skipped = len(case.findall("skipped"))
            case_failures = len(case.findall("failure"))
            case_errors = len(case.findall("error"))
            if case_skipped > 1 or case_failures > 1 or case_errors > 1:
                fail(f"JUnit testcase has duplicate outcome elements: {class_name}.{method_name}")
            suite_skipped += case_skipped
            suite_failures += case_failures
            suite_errors += case_errors
        declared = {
            key: int(suite.get(key, "-1"))
            for key in ("tests", "skipped", "failures", "errors")
        }
        actual = {
            "tests": len(cases),
            "skipped": suite_skipped,
            "failures": suite_failures,
            "errors": suite_errors,
        }
        if declared != actual:
            fail(f"JUnit suite counters differ at {path}: declared={declared} actual={actual}")
        skipped += suite_skipped
        failures += suite_failures
        errors += suite_errors

    required = Counter((row["CLASS"], row["METHOD_OR_SCOPE"]) for row in rows)
    if identities != required:
        fail(f"executed test identities differ: {dict(identities)}")
    if selected_total != len(rows):
        fail(f"selected total differs: required={len(rows)} selected={selected_total}")
    if skipped or failures or errors:
        fail(
            "required conformance tests did not all pass: "
            f"skipped={skipped} failures={failures} errors={errors}"
        )
    return selected_total, skipped, failures, errors


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--manifest",
        default="docs/architecture/governance/automated-guards/phase17-sandbox-conformance-tests.tsv",
    )
    parser.add_argument("--results-root", action="append", required=True)
    parser.add_argument("--freshness-marker", required=True)
    parser.add_argument("--expected-sha", required=True)
    args = parser.parse_args()

    rows = parse_manifest((ROOT / args.manifest).resolve())
    require_exact_sha(args.expected_sha)
    started_at_ns = read_freshness_marker(
        (ROOT / args.freshness_marker).resolve(), args.expected_sha
    )
    executed, skipped, failures, errors = parse_results(
        [(ROOT / value).resolve() for value in args.results_root], rows, started_at_ns
    )
    print(
        "PHASE17_SANDBOX_CONFORMANCE_GUARD=PASS "
        f"required={len(rows)} executed={executed} skipped={skipped} "
        f"failures={failures} errors={errors} sha={args.expected_sha}"
    )


if __name__ == "__main__":
    main()
