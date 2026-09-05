#!/usr/bin/env python3
"""JUnit XML accounting with commit/profile and exact test-identity checks."""

from __future__ import annotations

import argparse
import csv
import hashlib
import subprocess
import time
import xml.etree.ElementTree as element_tree
from dataclasses import dataclass
from pathlib import Path


MANIFEST_COLUMNS = ("COMMIT", "PROFILE", "RESULTS_ROOT", "RUN_STARTED_AT_EPOCH")
UNIVERSE_COLUMNS = ("TASK", "CLASSNAME", "TESTNAME")


@dataclass(frozen=True)
class Accounting:
    total: int
    passed: int
    failures: int
    errors: int
    skipped: int
    identities: tuple[tuple[str, str, str], ...]


def _read_single_manifest(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        fields = tuple(reader.fieldnames or ())
        # The three-field form is accepted only for focused negative controls;
        # production V2 invocations use the timestamped four-field form.
        if fields not in (MANIFEST_COLUMNS, MANIFEST_COLUMNS[:3]):
            raise ValueError(f"manifest header mismatch: {fields}")
        rows = list(reader)
    if len(rows) != 1:
        raise ValueError("run manifest must contain exactly one row")
    return rows[0]


def _xml_reports(results_root: Path) -> list[Path]:
    reports = sorted(results_root.glob("**/build/test-results/*/TEST-*.xml"))
    if not reports:
        raise ValueError("no JUnit XML reports found; tests did not execute")
    return reports


def _task_for_report(results_root: Path, report: Path) -> str:
    relative = report.relative_to(results_root)
    parts = relative.parts
    build_index = parts.index("build")
    project = ":" + ":".join(parts[:build_index])
    return f"{project}:{parts[build_index + 2]}"


def read_expected_universe(path: Path) -> tuple[tuple[str, str, str], ...]:
    """Read a frozen raw-recursive JUnit identity baseline supplied by the caller."""
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != UNIVERSE_COLUMNS:
            raise ValueError("expected universe header mismatch")
        expected = tuple(sorted((row["TASK"], row["CLASSNAME"], row["TESTNAME"]) for row in reader))
    if not expected or any(not all(identity) for identity in expected):
        raise ValueError("expected universe contains blank or zero identities")
    if len(expected) != len(set(expected)):
        raise ValueError("expected universe contains duplicate identity")
    return expected


def validate_accounting(
    results_root: Path,
    manifest_path: Path,
    *,
    expected_commit: str,
    expected_profile: str,
    expected_universe: Path | None = None,
    expected_skipped: int | None = None,
) -> Accounting:
    manifest = _read_single_manifest(manifest_path)
    if manifest["COMMIT"] != expected_commit:
        raise ValueError(f"wrong commit: expected {expected_commit}, got {manifest['COMMIT']}")
    if manifest["PROFILE"] != expected_profile:
        raise ValueError(f"wrong profile: expected {expected_profile}, got {manifest['PROFILE']}")
    declared_root = Path(manifest["RESULTS_ROOT"]).resolve()
    if declared_root != results_root.resolve():
        raise ValueError(f"manifest results root mismatch: {declared_root} != {results_root.resolve()}")
    started = manifest.get("RUN_STARTED_AT_EPOCH")
    if started is not None:
        try:
            started_at = float(started)
        except ValueError as exc:
            raise ValueError("invalid RUN_STARTED_AT_EPOCH") from exc
    else:
        started_at = None

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    identities: list[tuple[str, str, str]] = []
    for report in _xml_reports(results_root):
        if started_at is not None and report.stat().st_mtime < started_at:
            raise ValueError(f"stale report predates run manifest: {report}")
        root = element_tree.parse(report).getroot()
        if root.tag != "testsuite":
            raise ValueError(f"unsupported JUnit report root {root.tag}: {report}")
        declared_tests = int(root.attrib.get("tests", "0"))
        cases = root.findall("testcase")
        if declared_tests != len(cases):
            raise ValueError(f"JUnit arithmetic mismatch in {report}: declared={declared_tests}, cases={len(cases)}")
        for key in totals:
            totals[key] += int(root.attrib.get(key, "0"))
        task = _task_for_report(results_root, report)
        for case in cases:
            identity = (task, case.attrib.get("classname", ""), case.attrib.get("name", ""))
            if not all(identity):
                raise ValueError(f"JUnit testcase without stable identity: {report}")
            identities.append(identity)
    if len(identities) != len(set(identities)):
        raise ValueError("duplicate JUnit testcase identity detected")
    if totals["tests"] != len(identities):
        raise ValueError(f"total arithmetic mismatch: suites={totals['tests']}, identities={len(identities)}")
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    if passed < 0:
        raise ValueError("negative passed count from JUnit arithmetic")
    if totals["tests"] == 0:
        raise ValueError("zero tests executed")
    if totals["failures"] or totals["errors"]:
        raise ValueError(f"JUnit failures/errors present: {totals['failures']}/{totals['errors']}")
    if expected_skipped is not None and totals["skipped"] != expected_skipped:
        raise ValueError(f"skip drift: expected {expected_skipped}, got {totals['skipped']}")

    observed = tuple(sorted(identities))
    if expected_universe is not None:
        expected = read_expected_universe(expected_universe)
        if observed != expected:
            missing = len(set(expected) - set(observed))
            extra = len(set(observed) - set(expected))
            raise ValueError(f"JUnit identity/universe drift: missing={missing}, extra={extra}")
    return Accounting(totals["tests"], passed, totals["failures"], totals["errors"], totals["skipped"], observed)


def current_commit(root: Path) -> str:
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()


def write_manifest(path: Path, results_root: Path, profile: str, root: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=MANIFEST_COLUMNS, delimiter="\t")
        writer.writeheader()
        writer.writerow({"COMMIT": current_commit(root), "PROFILE": profile,
                         "RESULTS_ROOT": str(results_root.resolve()), "RUN_STARTED_AT_EPOCH": f"{time.time():.6f}"})


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--expected-profile", required=True)
    parser.add_argument("--expected-universe", type=Path)
    parser.add_argument("--expected-skipped", type=int)
    args = parser.parse_args()
    accounting = validate_accounting(args.results_root.resolve(), args.manifest.resolve(),
                                     expected_commit=args.expected_commit, expected_profile=args.expected_profile,
                                     expected_universe=args.expected_universe,
                                     expected_skipped=args.expected_skipped)
    digest = hashlib.sha256(repr(accounting.identities).encode()).hexdigest()
    print(f"PASS junit-accounting total={accounting.total} passed={accounting.passed} skipped={accounting.skipped} universe_sha256={digest}")


if __name__ == "__main__":
    main()
