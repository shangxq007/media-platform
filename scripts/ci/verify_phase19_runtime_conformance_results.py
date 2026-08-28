#!/usr/bin/env python3
"""Fail-closed verifier for fresh Phase 19 runtime-conformance JUnit XML."""

from __future__ import annotations

import json
import os
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REQUIRED_METHODS = (
    "nonzero_and_cancellation_publish_no_artifact_or_completion",
    "bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority",
    "real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion",
)
REQUIRED_MODULES = (
    "ffmpeg-provider-module",
    "sandbox-isolation-module",
    "worker-fabric-module",
    "provider-plugin-runtime-module",
    "artifact-module",
    "platform-distribution",
)
MAX_MARKER_BYTES = 4096
MAX_XML_FILES = 4096
MAX_XML_BYTES = 32 * 1024 * 1024


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def required_sha(name: str) -> str:
    value = os.environ.get(name, "")
    if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
        fail(f"{name} is not an exact lowercase Git SHA")
    return value


def load_freshness_floor_ns() -> int:
    marker_value = os.environ.get("PHASE19_RUNTIME_START_MARKER", "")
    if not marker_value:
        fail("PHASE19_RUNTIME_START_MARKER is empty")
    marker = (ROOT / marker_value).resolve() if not Path(marker_value).is_absolute() else Path(marker_value).resolve()
    try:
        marker.relative_to(ROOT)
    except ValueError:
        fail("runtime start marker is outside the checkout")
    if not marker.is_file():
        fail("runtime start marker is missing")
    marker_stat = marker.stat()
    if marker_stat.st_size <= 0 or marker_stat.st_size > MAX_MARKER_BYTES:
        fail("runtime start marker is missing or unbounded")
    try:
        payload = json.loads(marker.read_text())
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        fail(f"runtime start marker is invalid: {error}")
    expected_sha = required_sha("EXPECTED_SHA")
    github_sha = required_sha("GITHUB_SHA")
    if payload.get("expected_sha") != expected_sha or payload.get("github_sha") != github_sha:
        fail("runtime start marker SHA identity differs from the environment")
    started_at_ns = payload.get("started_at_ns")
    if not isinstance(started_at_ns, int) or started_at_ns <= 0:
        fail("runtime start marker timestamp is invalid")
    return marker_stat.st_mtime_ns


def canonical_method(name: str) -> str:
    return name[:-2] if name.endswith("()") else name


def declared_count(suite: ET.Element, attribute: str, xml_file: Path) -> int:
    value = suite.attrib.get(attribute)
    if value is None and attribute == "skipped":
        value = "0"
    try:
        count = int(value) if value is not None else -1
    except ValueError:
        fail(f"JUnit XML has a non-integer {attribute} count: {xml_file.relative_to(ROOT)}")
    if count < 0:
        fail(f"JUnit XML lacks a bounded {attribute} count: {xml_file.relative_to(ROOT)}")
    return count


def main() -> None:
    freshness_floor_ns = load_freshness_floor_ns()
    xml_files: list[Path] = []
    module_file_counts: dict[str, int] = {}
    for module in REQUIRED_MODULES:
        root = ROOT / module
        module_xml = sorted(root.glob("build/test-results/test/TEST-*.xml"))
        if not module_xml:
            fail(f"required module has no JUnit XML: {module}")
        module_file_counts[module] = len(module_xml)
        xml_files.extend(module_xml)
    if len(xml_files) > MAX_XML_FILES:
        fail("JUnit XML file count exceeds the bounded maximum")

    tests = failures = errors = skipped = 0
    required_statuses: dict[str, list[str]] = {method: [] for method in REQUIRED_METHODS}
    for xml_file in xml_files:
        stat = xml_file.stat()
        if stat.st_mtime_ns < freshness_floor_ns:
            fail(f"stale JUnit XML predates the runtime marker: {xml_file.relative_to(ROOT)}")
        if stat.st_size <= 0 or stat.st_size > MAX_XML_BYTES:
            fail(f"JUnit XML size is empty or unbounded: {xml_file.relative_to(ROOT)}")
        try:
            document = ET.parse(xml_file)
        except (OSError, ET.ParseError) as error:
            fail(f"JUnit XML is unreadable: {xml_file.relative_to(ROOT)}: {error}")
        xml_root = document.getroot()
        suites = [xml_root] if xml_root.tag == "testsuite" else list(xml_root.findall("testsuite"))
        if not suites:
            fail(f"JUnit XML contains no testsuite: {xml_file.relative_to(ROOT)}")
        declared_tests = sum(declared_count(suite, "tests", xml_file) for suite in suites)
        declared_failures = sum(declared_count(suite, "failures", xml_file) for suite in suites)
        declared_errors = sum(declared_count(suite, "errors", xml_file) for suite in suites)
        declared_skipped = sum(declared_count(suite, "skipped", xml_file) for suite in suites)
        file_tests = file_failures = file_errors = file_skipped = 0
        for testcase in xml_root.iter("testcase"):
            file_tests += 1
            has_failure = testcase.find("failure") is not None
            has_error = testcase.find("error") is not None
            has_skip = testcase.find("skipped") is not None
            status_count = int(has_failure) + int(has_error) + int(has_skip)
            if status_count > 1:
                fail(f"JUnit testcase has multiple terminal states: {xml_file.relative_to(ROOT)}")
            file_failures += int(has_failure)
            file_errors += int(has_error)
            file_skipped += int(has_skip)
            status = "failed" if has_failure else "error" if has_error else "skipped" if has_skip else "passed"
            class_name = testcase.attrib.get("classname", "")
            method = canonical_method(testcase.attrib.get("name", ""))
            if class_name.endswith(".FfmpegClosedLoopIntegrationTest") and method in required_statuses:
                required_statuses[method].append(status)
        declared = (declared_tests, declared_failures, declared_errors, declared_skipped)
        observed = (file_tests, file_failures, file_errors, file_skipped)
        if declared != observed:
            fail(f"JUnit XML declared/observed arithmetic differs: {xml_file.relative_to(ROOT)}")
        tests += file_tests
        failures += file_failures
        errors += file_errors
        skipped += file_skipped

    if failures != 0 or errors != 0:
        fail(f"required module aggregate is not green: failures={failures} errors={errors}")
    for method, statuses in required_statuses.items():
        if len(statuses) != 1:
            fail(f"required FFmpeg method did not execute exactly once: {method} count={len(statuses)}")
    required_skipped = sum(status == "skipped" for statuses in required_statuses.values() for status in statuses)
    required_executed = sum(status == "passed" for statuses in required_statuses.values() for status in statuses)
    if required_skipped != 0:
        fail(f"required FFmpeg methods were skipped: {required_skipped}")
    if required_executed != len(REQUIRED_METHODS):
        fail(f"required FFmpeg methods did not all pass: {required_executed}/{len(REQUIRED_METHODS)}")

    passed = tests - failures - errors - skipped
    if passed < 0 or passed + failures + errors + skipped != tests:
        fail("JUnit aggregate arithmetic is inconsistent")
    module_counts = ",".join(f"{module}:{module_file_counts[module]}" for module in REQUIRED_MODULES)
    print(
        "PHASE19_RUNTIME_CONFORMANCE_RESULTS=PASS "
        f"modules={len(REQUIRED_MODULES)} xml_files={len(xml_files)} tests={tests} passed={passed} "
        f"failures={failures} errors={errors} skipped={skipped} "
        f"required_methods={len(REQUIRED_METHODS)} required_executed={required_executed} "
        f"required_skipped={required_skipped} required_nonexecution=0 module_xml={module_counts}"
    )


if __name__ == "__main__":
    main()
