#!/usr/bin/env python3
"""Fail-closed verifier for fresh targeted Phase 19 FFmpeg JUnit XML."""

from __future__ import annotations

import json
import os
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
XML_FILE = (
    ROOT
    / "ffmpeg-provider-module/build/test-results/test/"
    "TEST-com.example.platform.ffmpeg.FfmpegClosedLoopIntegrationTest.xml"
)
REQUIRED_CLASS = "com.example.platform.ffmpeg.FfmpegClosedLoopIntegrationTest"
REQUIRED_METHODS = (
    "real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion",
    "bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority",
    "nonzero_and_cancellation_publish_no_artifact_or_completion",
)
MAX_MARKER_BYTES = 4096
MAX_XML_BYTES = 4 * 1024 * 1024


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def required_sha(name: str) -> str:
    value = os.environ.get(name, "")
    if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
        fail(f"{name} is not an exact lowercase Git SHA")
    return value


def freshness_floor_ns() -> int:
    marker_value = os.environ.get("PHASE19_TARGETED_RUNTIME_START_MARKER", "")
    if not marker_value:
        fail("PHASE19_TARGETED_RUNTIME_START_MARKER is empty")
    marker_path = Path(marker_value)
    marker = (ROOT / marker_path).resolve() if not marker_path.is_absolute() else marker_path.resolve()
    try:
        marker.relative_to(ROOT)
    except ValueError:
        fail("targeted runtime start marker is outside the checkout")
    if not marker.is_file():
        fail("targeted runtime start marker is missing")
    marker_stat = marker.stat()
    if marker_stat.st_size <= 0 or marker_stat.st_size > MAX_MARKER_BYTES:
        fail("targeted runtime start marker is empty or unbounded")
    try:
        payload = json.loads(marker.read_text())
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        fail(f"targeted runtime start marker is invalid: {error}")
    expected_sha = required_sha("EXPECTED_SHA")
    github_sha = required_sha("GITHUB_SHA")
    if payload.get("expected_sha") != expected_sha or payload.get("github_sha") != github_sha:
        fail("targeted runtime start marker SHA identity differs from the environment")
    started_at_ns = payload.get("started_at_ns")
    if not isinstance(started_at_ns, int) or started_at_ns <= 0:
        fail("targeted runtime start marker timestamp is invalid")
    return marker_stat.st_mtime_ns


def declared_count(suite: ET.Element, attribute: str) -> int:
    value = suite.attrib.get(attribute, "0" if attribute == "skipped" else None)
    try:
        count = int(value) if value is not None else -1
    except ValueError:
        fail(f"targeted JUnit XML has a non-integer {attribute} count")
    if count < 0:
        fail(f"targeted JUnit XML lacks a bounded {attribute} count")
    return count


def canonical_method(name: str) -> str:
    return name[:-2] if name.endswith("()") else name


def main() -> None:
    marker_mtime_ns = freshness_floor_ns()
    if not XML_FILE.is_file():
        fail("FfmpegClosedLoopIntegrationTest JUnit XML is missing")
    xml_stat = XML_FILE.stat()
    if xml_stat.st_mtime_ns < marker_mtime_ns:
        fail("FfmpegClosedLoopIntegrationTest JUnit XML predates the targeted marker")
    if xml_stat.st_size <= 0 or xml_stat.st_size > MAX_XML_BYTES:
        fail("FfmpegClosedLoopIntegrationTest JUnit XML is empty or unbounded")
    try:
        document = ET.parse(XML_FILE)
    except (OSError, ET.ParseError) as error:
        fail(f"FfmpegClosedLoopIntegrationTest JUnit XML is unreadable: {error}")
    suite = document.getroot()
    if suite.tag != "testsuite":
        fail("FfmpegClosedLoopIntegrationTest JUnit XML root is not testsuite")

    declared_tests = declared_count(suite, "tests")
    declared_failures = declared_count(suite, "failures")
    declared_errors = declared_count(suite, "errors")
    declared_skipped = declared_count(suite, "skipped")
    observed_tests = observed_failures = observed_errors = observed_skipped = 0
    statuses: dict[str, list[str]] = {method: [] for method in REQUIRED_METHODS}
    for testcase in suite.iter("testcase"):
        observed_tests += 1
        has_failure = testcase.find("failure") is not None
        has_error = testcase.find("error") is not None
        has_skip = testcase.find("skipped") is not None
        if int(has_failure) + int(has_error) + int(has_skip) > 1:
            fail("targeted JUnit testcase has multiple terminal states")
        observed_failures += int(has_failure)
        observed_errors += int(has_error)
        observed_skipped += int(has_skip)
        class_name = testcase.attrib.get("classname", "")
        method = canonical_method(testcase.attrib.get("name", ""))
        if class_name == REQUIRED_CLASS and method in statuses:
            status = "failed" if has_failure else "error" if has_error else "skipped" if has_skip else "passed"
            statuses[method].append(status)

    declared = (declared_tests, declared_failures, declared_errors, declared_skipped)
    observed = (observed_tests, observed_failures, observed_errors, observed_skipped)
    if declared != observed:
        fail("targeted JUnit XML declared/observed arithmetic differs")
    if declared_tests != 3:
        fail(f"targeted JUnit XML must declare exactly 3 tests: {declared_tests}")
    if declared_failures != 0 or declared_errors != 0 or declared_skipped != 0:
        fail(
            "targeted JUnit XML is not green: "
            f"failures={declared_failures} errors={declared_errors} skipped={declared_skipped}"
        )
    for method, method_statuses in statuses.items():
        if method_statuses != ["passed"]:
            fail(f"required targeted method did not pass exactly once: {method} statuses={method_statuses}")

    passed = declared_tests - declared_failures - declared_errors - declared_skipped
    if passed + declared_failures + declared_errors + declared_skipped != declared_tests:
        fail("targeted JUnit aggregate arithmetic is inconsistent")
    print(
        "PHASE19_TARGETED_RUNTIME_RESULTS=PASS "
        f"tests={declared_tests} passed={passed} failures={declared_failures} "
        f"errors={declared_errors} skipped={declared_skipped} "
        f"required_methods={len(REQUIRED_METHODS)} required_passed={len(REQUIRED_METHODS)}"
    )


if __name__ == "__main__":
    main()
