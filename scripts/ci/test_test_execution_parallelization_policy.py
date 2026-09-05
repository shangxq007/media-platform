#!/usr/bin/env python3
"""Focused executable contract for TEST_EXECUTION_PARALLELIZATION_AND_VALIDATION_POLICY_V1."""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STANDARD_CI = ROOT / ".github/workflows/ci.yml"
TEST_EXECUTION_VALIDATION_CI = ROOT / ".github/workflows/test-execution-validation.yml"
EXPECTED_TEST_EXECUTION_VALIDATION_PATHS = (
    "build.gradle.kts",
    "governance/TEST_EXECUTION_TOPOLOGY_LEDGER.tsv",
    "governance/TEST_EXECUTION_SERIAL_ONLY_LEDGER.tsv",
    "governance/TEST_EXECUTION_ACCOUNTING_BASELINE.tsv",
    "governance/TEST_EXECUTION_CANDIDATE_ENROLLMENT.tsv",
    "governance/TEST_EXECUTION_DECLARED_OVERLAPS.tsv",
    "governance/TEST_EXECUTION_PARALLELIZATION_PROMOTION_RECEIPT_V1.tsv",
    "governance/TEST_EXECUTION_VALIDATION_LEVELS.tsv",
    "governance/CI_VALIDATION_DEPENDENCY_GRAPH.tsv",
    "governance/NEXT_DEBT_PARALLELIZATION_PLAN.tsv",
    "scripts/ci/census_test_execution_topology.py",
    "scripts/ci/run_full_deterministic_backend_suite.py",
    "scripts/ci/run_test_execution_benchmark.py",
    "scripts/ci/test_execution_accounting.py",
    "scripts/ci/test_execution_policy.py",
    "scripts/ci/test_test_execution_parallelization_policy.py",
    ".github/workflows/test-execution-validation.yml",
)

MUTATION_REMOVALS = (
    "governance/TEST_EXECUTION_TOPOLOGY_LEDGER.tsv",
    "governance/TEST_EXECUTION_SERIAL_ONLY_LEDGER.tsv",
    "governance/TEST_EXECUTION_PARALLELIZATION_PROMOTION_RECEIPT_V1.tsv",
    "governance/CI_VALIDATION_DEPENDENCY_GRAPH.tsv",
    "scripts/ci/test_execution_policy.py",
)
sys.path.insert(0, str(Path(__file__).resolve().parent))

from test_execution_policy import (  # noqa: E402 - intentionally absent during TDD RED
    POLICY_LEVELS,
    REQUIRED_ACCOUNTING_BASELINE_COLUMNS,
    REQUIRED_CI_GRAPH_COLUMNS,
    REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS,
    REQUIRED_DEBT_COLUMNS,
    REQUIRED_OVERLAP_COLUMNS,
    REQUIRED_LEDGER_COLUMNS,
    REQUIRED_SERIAL_COLUMNS,
    validate_repository,
)


def parse_pull_request_paths(workflow_text: str) -> list[str]:
    lines = workflow_text.splitlines()
    pull_request_indent: int | None = None
    paths_indent: int | None = None
    in_paths = False
    paths: list[str] = []
    for raw_line in lines:
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        if pull_request_indent is None:
            if stripped == "pull_request:":
                pull_request_indent = indent
            continue
        if indent <= pull_request_indent:
            break
        if in_paths:
            if indent <= paths_indent:
                in_paths = False
                continue
            if stripped.startswith("- "):
                paths.append(stripped[2:])
            continue
        if stripped == "paths:" and indent > pull_request_indent:
            paths_indent = indent
            in_paths = True
    return paths


def remove_pull_request_path(workflow_text: str, removed: str) -> tuple[str, bool]:
    lines = workflow_text.splitlines()
    pull_request_indent: int | None = None
    paths_indent: int | None = None
    in_paths = False
    removed_once = False
    output: list[str] = []
    for raw_line in lines:
        stripped = raw_line.strip()
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        if pull_request_indent is None:
            if stripped == "pull_request:":
                pull_request_indent = indent
            output.append(raw_line)
            continue
        if indent <= pull_request_indent:
            if not raw_line.strip() or stripped == "":
                output.append(raw_line)
                continue
            pull_request_indent = None
            in_paths = False
            paths_indent = None
            output.append(raw_line)
            continue
        if in_paths:
            if indent <= paths_indent:
                in_paths = False
                output.append(raw_line)
                continue
            if stripped.startswith("- "):
                candidate = stripped[2:]
                if not removed_once and candidate == removed:
                    removed_once = True
                    continue
            output.append(raw_line)
            continue
        if stripped == "paths:" and indent > pull_request_indent:
            paths_indent = indent
            in_paths = True
        output.append(raw_line)
    return "\n".join(output), removed_once


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def test_repository_policy() -> None:
    import test_execution_policy as policy

    declared = policy.declared_test_tasks(ROOT)
    require(len(declared) == 51, f"expected 51 declared active Test tasks, got {len(declared)}")
    require((":platform-algorithms", "test") in declared,
            "realized platform-algorithms parent Test task is missing from the declared census")
    report = validate_repository(ROOT)
    require(report.task_count == 51, f"expected 51 active Test tasks, got {report.task_count}")
    require(report.serial_task_count > 0, "serial-only ledger must contain explicit tasks")
    require(report.pure_task_count == 21, "the 21 Hermes-sealed candidates must be canonically PURE_PARALLEL_SAFE")
    require(POLICY_LEVELS == ("V0_PRE_COMMIT", "V1_IMPLEMENTATION", "V2_CANDIDATE_FCV", "V3_CHECKPOINT"),
            "validation levels changed")


def test_candidate_enrollment_is_the_complete_sealed_pure_census() -> None:
    import test_execution_policy as policy

    expected = {
        (":ai-module", "test"), (":artifact-module", "test"), (":audio-module", "test"),
        (":audit-compliance-module", "test"), (":bmf-provider-module", "test"),
        (":cloud-resource-module", "test"), (":color-image-module", "test"),
        (":composite-resource-module", "test"), (":entitlement-module", "test"),
        (":extension-module", "test"), (":federation-query-module", "test"),
        (":identity-access-module", "test"), (":media-execution-plan-module", "test"),
        (":operation-module", "test"), (":outbox-event-module", "test"),
        (":payment-module", "test"), (":shared-kernel", "test"), (":social-publish-module", "test"),
        (":timeline-module", "test"), (":user-analytics-module", "test"),
        (":worker-fabric-module", "test"),
    }
    rows = policy.read_tsv(ROOT / policy.CANDIDATE_ENROLLMENT, REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS)
    actual = {(row["PROJECT"], row["TEST_TASK"]) for row in rows}
    require(actual == expected, f"candidate enrollment drift: missing={expected - actual}, extra={actual - expected}")
    require(len(rows) == 21, f"expected 21 candidate rows, got {len(rows)}")
    for row in rows:
        require(row["PROPOSED_CLASSIFICATION"] == "PURE_PARALLEL_SAFE", "candidate is not proposed pure")
        require(row["BENCHMARK_EVIDENCE_REFERENCE"] == policy.PROMOTION_RECEIPT_PATH.as_posix(),
                "candidate benchmark receipt reference drifted")
        require(row["WINNER_REPLAY_EVIDENCE_REFERENCE"] == policy.PROMOTION_RECEIPT_PATH.as_posix(),
                "candidate replay receipt reference drifted")
        require(row["PROMOTION_STATUS"] == "SEALED_PROMOTION", "candidate was not promoted from Hermes evidence")


def test_candidate_enrollment_rejects_unknown_or_changed_sealed_promotion() -> None:
    import test_execution_policy as policy

    topology_rows = policy.read_tsv(ROOT / policy.TOPOLOGY_LEDGER, REQUIRED_LEDGER_COLUMNS)
    topology = {(row["PROJECT"], row["TEST_TASK"]): dict(row) for row in topology_rows}
    enrollment = policy.read_tsv(ROOT / policy.CANDIDATE_ENROLLMENT, REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS)
    unknown = [dict(row) for row in enrollment]
    unknown[0]["PROJECT"] = ":unknown-candidate-module"
    try:
        policy.validate_candidate_enrollment(topology, unknown)
    except ValueError as exc:
        require("candidate enrollment" in str(exc).lower(), str(exc))
    else:
        raise AssertionError("unknown candidate enrollment was accepted")
    unsealed_topology = {key: dict(row) for key, row in topology.items()}
    unsealed_topology[(":ai-module", "test")]["CLASSIFICATION"] = "REVIEW_REQUIRED"
    try:
        policy.validate_candidate_enrollment(unsealed_topology, enrollment)
    except ValueError as exc:
        require("sealed promotion" in str(exc).lower(), str(exc))
    else:
        raise AssertionError("sealed promotion without canonical topology was accepted")
    changed_reference = [dict(row) for row in enrollment]
    changed_reference[0]["BENCHMARK_EVIDENCE_REFERENCE"] = "governance/changed-receipt.tsv"
    try:
        policy.validate_candidate_enrollment(topology, changed_reference)
    except ValueError as exc:
        require("receipt" in str(exc).lower(), str(exc))
    else:
        raise AssertionError("changed sealed promotion receipt reference was accepted")


def test_promotion_receipt_has_the_exact_hermes_results() -> None:
    import test_execution_policy as policy

    receipt = policy.read_promotion_receipt(ROOT / policy.PROMOTION_RECEIPT_PATH)
    require(receipt["MATRIX_TSV_SHA256"] == "c2c18d0c599a24c97ced2aeefa6818b825195e08f203e74ed0538afcaa3cc397",
            "matrix TSV SHA256 drifted")
    require(receipt["WINNER_REPLAY_TSV_SHA256"] == "7b128a1d236fa03f1d23101f89e4471cd4f7c4f0e2d105560331d1ec48d3f17c",
            "winner replay TSV SHA256 drifted")
    require(receipt["WINNER_WORKERS"] == "24" and receipt["PURE_FORKS"] == "2"
            and receipt["SPRING_FORKS"] == "1" and receipt["SERIAL_FORKS"] == "1",
            "winner scheduling profile drifted")
    require(receipt["OLD_PER_EPOCH_SERIAL_POLICY_STATUS"] == "STILL_AUTHORITATIVE_UNTIL_THIS_FOUNDATION_IS_PUBLISHED",
            "old per-epoch serial policy status drifted")


def test_promotion_receipt_fails_closed_when_missing_or_changed() -> None:
    import test_execution_policy as policy

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        receipt_path = root / policy.PROMOTION_RECEIPT_PATH
        try:
            policy.validate_promotion_receipt(root)
        except ValueError as exc:
            require("missing required policy artifact" in str(exc).lower(), str(exc))
        else:
            raise AssertionError("missing promotion receipt was accepted")
        receipt_path.parent.mkdir(parents=True)
        receipt_path.write_text((ROOT / policy.PROMOTION_RECEIPT_PATH).read_text(encoding="utf-8"), encoding="utf-8")
        policy.validate_promotion_receipt(root)
        receipt_path.write_text(
            receipt_path.read_text(encoding="utf-8").replace("\t1331.304\n", "\t1331.305\n", 1),
            encoding="utf-8",
        )
        try:
            policy.validate_promotion_receipt(root)
        except ValueError as exc:
            require("exact hermes-controlled result" in str(exc).lower(), str(exc))
        else:
            raise AssertionError("changed promotion receipt was accepted")


def test_policy_has_required_machine_readable_columns() -> None:
    import test_execution_policy as policy

    for path, columns in (
        (policy.TOPOLOGY_LEDGER, REQUIRED_LEDGER_COLUMNS),
        (policy.SERIAL_LEDGER, REQUIRED_SERIAL_COLUMNS),
        (policy.CI_GRAPH, REQUIRED_CI_GRAPH_COLUMNS),
        (policy.NEXT_DEBT_PLAN, REQUIRED_DEBT_COLUMNS),
        (policy.DECLARED_OVERLAPS, REQUIRED_OVERLAP_COLUMNS),
        (policy.ACCOUNTING_BASELINE, REQUIRED_ACCOUNTING_BASELINE_COLUMNS),
        (policy.CANDIDATE_ENROLLMENT, REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS),
    ):
        first = (ROOT / path).read_text(encoding="utf-8").splitlines()[0].split("\t")
        require(tuple(first) == columns, f"{path}: header is not the frozen contract")


def test_negative_controls_reject_unsafe_edits() -> None:
    import test_execution_policy as policy

    ledger = ROOT / policy.TOPOLOGY_LEDGER
    original = ledger.read_text(encoding="utf-8")
    try:
        ledger.write_text(original.replace("\tSERIAL_ONLY\t", "\tPURE_PARALLEL_SAFE\t", 1), encoding="utf-8")
        try:
            validate_repository(ROOT)
        except ValueError as exc:
            require(any(token in str(exc).lower() for token in ("serial", "fork", "evidence")), str(exc))
        else:
            raise AssertionError("unsafe SERIAL_ONLY -> PURE_PARALLEL_SAFE mutation was accepted")
    finally:
        ledger.write_text(original, encoding="utf-8")


def test_accounting_rejects_arithmetic_and_identity_drift() -> None:
    from test_execution_accounting import validate_accounting

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        results = root / "module" / "build" / "test-results" / "test"
        results.mkdir(parents=True)
        (results / "TEST-example.xml").write_text(
            '<testsuite name="example" tests="2" failures="0" errors="0" skipped="0">'
            '<testcase classname="example.C" name="one"/><testcase classname="example.C" name="two"/>'
            '</testsuite>', encoding="utf-8")
        manifest = root / "run-manifest.tsv"
        manifest.write_text("COMMIT\tPROFILE\tRESULTS_ROOT\nunknown\tV2_CANDIDATE_FCV\t.\n", encoding="utf-8")
        try:
            validate_accounting(root, manifest, expected_commit="different", expected_profile="V2_CANDIDATE_FCV")
        except ValueError as exc:
            require("commit" in str(exc).lower(), str(exc))
        else:
            raise AssertionError("wrong commit was accepted")
        manifest.write_text(
            "COMMIT\tPROFILE\tRESULTS_ROOT\tRUN_STARTED_AT_EPOCH\n"
            f"candidate\tV2_CANDIDATE_FCV\t{root}\t0\n", encoding="utf-8")
        (results / "TEST-example.xml").write_text(
            '<testsuite name="example" tests="2" failures="0" errors="0" skipped="0">'
            '<testcase classname="example.C" name="one"/>'
            '</testsuite>', encoding="utf-8")
        try:
            validate_accounting(root, manifest, expected_commit="candidate", expected_profile="V2_CANDIDATE_FCV")
        except ValueError as exc:
            require("arithmetic" in str(exc).lower(), str(exc))
        else:
            raise AssertionError("JUnit arithmetic mismatch was accepted")


def test_gradle_uses_explicit_parallel_then_restricted_phases() -> None:
    build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    require("SPRING_HEAVY_BOUNDED" in build, "Gradle does not recognize bounded Spring tasks")
    require("parallelEligibleTestTasks" in build and "restrictedTestTasks" in build,
            "Gradle lacks explicit task scheduling lanes")
    require("mustRunAfter(parallelEligibleTestTasks)" in build,
            "restricted tasks are not forced after the parallel phase")
    require("mustRunAfter(previous)" in build,
            "restricted tasks are not mechanically serialized")
    require("testExecutionCandidateProfile" in build,
            "Gradle lacks the explicit benchmark-only candidate profile")
    require("testExecutionCandidateEnrollmentLedger" in build,
            "candidate profile does not consume the frozen enrollment source")
    require("effectiveTestExecutionTopology" in build,
            "candidate profile cannot safely derive effective topology")
    require('filterValues { status -> status == "CANDIDATE_ONLY" }' in build,
            "candidate profile must overlay only future unsealed enrollment")
    require("require(candidateProfileTaskPaths.isNotEmpty())" not in build,
            "candidate profile must remain configuration-valid after every enrolled row is sealed")
    require("activeTaskPaths == testExecutionTopology.keys" in build,
            "Gradle census does not compare the exact active Test task paths")
    require("testExecutionSchedulingCensus" in build,
            "Gradle cannot print the effective configured scheduling census")


def test_census_check_alias_is_real_cli_contract() -> None:
    completed = subprocess.run(
        [sys.executable, "-B", str(ROOT / "scripts/ci/census_test_execution_topology.py"), "--check"],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    require(completed.returncode == 0, f"--check failed: {completed.stdout}{completed.stderr}")
    require("PASS test-task-census" in completed.stdout, "--check did not run the census")


def test_test_execution_validation_is_separate_from_standard_ci_policy_summary() -> None:
    standard = STANDARD_CI.read_text(encoding="utf-8")
    summary = standard.split("\n  policy-summary:\n", 1)[1].split("\n  images:\n", 1)[0]
    require(
        "needs: [change-impact, backend, frontend, gitops-validation, formal-validation, semgrep]" in summary,
        "Standard CI policy-summary dependency contract changed",
    )
    require(TEST_EXECUTION_VALIDATION_CI.is_file(), "test execution validation must use a separate workflow")
    validation = TEST_EXECUTION_VALIDATION_CI.read_text(encoding="utf-8")
    for required in (
        "name: Test Execution Validation",
        "concurrency:",
        "cancel-in-progress: false",
        "python3 -B scripts/ci/test_execution_policy.py",
        "python3 -B scripts/ci/test_test_execution_parallelization_policy.py",
        "Canonical publication remains serialized and subject to publication review.",
    ):
        require(required in validation, f"test execution validation workflow is missing: {required}")
    for forbidden in ("contents: write", "pull-requests: write", "packages: write"):
        require(forbidden not in validation, f"validation workflow must not claim publication authority: {forbidden}")


def test_benchmark_uses_controlled_matrix_then_winner_replay() -> None:
    from run_test_execution_benchmark import (
        REPORT_COLUMNS,
        WORKER_PROFILES,
        _resource_collision_status,
        _run_command,
        _time_measurement,
        validate_benchmark_plan,
    )

    try:
        validate_benchmark_plan("matrix", 1, list(WORKER_PROFILES), None,
                                "./gradlew -PtestExecutionCandidateProfile=true test")
    except ValueError as exc:
        require("rerun" in str(exc), str(exc))
    else:
        raise AssertionError("matrix without forced execution was accepted")
    try:
        validate_benchmark_plan("matrix", 3, list(WORKER_PROFILES), None,
                                "./gradlew -PtestExecutionCandidateProfile=true --rerun-tasks test")
    except ValueError as exc:
        require("once" in str(exc), str(exc))
    else:
        raise AssertionError("matrix replayed every worker profile")
    try:
        validate_benchmark_plan("winner-replay", 2, [8], 8,
                                "./gradlew -PtestExecutionCandidateProfile=true --rerun-tasks test")
    except ValueError as exc:
        require("three" in str(exc), str(exc))
    else:
        raise AssertionError("winner replay accepted fewer than three runs")
    try:
        validate_benchmark_plan("winner-replay", 3, [8], 8, "./gradlew --rerun-tasks test")
    except ValueError as exc:
        require("candidate" in str(exc).lower(), str(exc))
    else:
        raise AssertionError("benchmark without candidate profile was accepted")
    command = validate_benchmark_plan(
        "winner-replay", 3, [8], 8,
        "./gradlew -PtestExecutionCandidateProfile=true --rerun-tasks test",
    )
    require(command == ["./gradlew", "-PtestExecutionCandidateProfile=true", "--rerun-tasks", "test"],
            "valid benchmark command changed")
    run_command = _run_command(command, 8)
    for required in ("-PtestExecutionCandidateProfile=true", "--rerun-tasks", "test", "--parallel", "--max-workers=8"):
        require(required in run_command, f"generated run command omitted {required}")
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        log = root / "run.log"
        log.write_text("ordinary successful test output\n", encoding="utf-8")
        require(_resource_collision_status(0, log) == "0", "clean captured output was not classified as collision-free")
        log.write_text("java.net.BindException: Address already in use\n", encoding="utf-8")
        require(_resource_collision_status(0, log) == "FAIL_CLOSED", "bind collision was not fail-closed")
        require(_resource_collision_status(1, log) == "FAIL_CLOSED", "nonzero exit was not fail-closed")
        time_v = root / "time-v.txt"
        time_v.write_text(
            "User time (seconds): 1.25\nSystem time (seconds): 0.75\n"
            "Maximum resident set size (kbytes): 4096\n", encoding="utf-8",
        )
        require(_time_measurement(time_v, 2.0) == ("4096", "100.00", "HIGH_PER_RUN_TIME_V"),
                "per-run time-v measurement was not parsed exactly")
    for field in ("WORKERS", "PURE_FORKS", "SPRING_FORKS", "SERIAL_FORKS", "WALL_SECONDS", "TESTS", "PASSED",
                  "SKIPPED", "FAILED", "ERRORS", "PEAK_RSS_KIB", "CPU_UTILIZATION_PERCENT",
                  "RESOURCE_COLLISIONS", "FLAKY_FAILURES", "STDOUT_STDERR_PATH", "TIME_V_PATH",
                  "MEASUREMENT_CONFIDENCE"):
        require(field in REPORT_COLUMNS, f"benchmark report is missing {field}")


def test_test_execution_validation_workflow_paths_match_authoritative_input_set() -> None:
    workflow = TEST_EXECUTION_VALIDATION_CI.read_text(encoding="utf-8")
    detected_paths = parse_pull_request_paths(workflow)
    require(tuple(detected_paths) == EXPECTED_TEST_EXECUTION_VALIDATION_PATHS,
            "test execution validation workflow paths drifted from authoritative input set")


def test_test_execution_validation_path_mutation_controls() -> None:
    original = TEST_EXECUTION_VALIDATION_CI.read_text(encoding="utf-8")
    require(tuple(parse_pull_request_paths(original)) == EXPECTED_TEST_EXECUTION_VALIDATION_PATHS,
            "cannot establish mutation-control baseline: current workflow path block is not canonical")

    for removed in MUTATION_REMOVALS:
        mutated, removed_ok = remove_pull_request_path(original, removed)
        require(removed_ok, f"mutation setup failed for {removed}")
        mutated_paths = parse_pull_request_paths(mutated)
        require(tuple(mutated_paths) != EXPECTED_TEST_EXECUTION_VALIDATION_PATHS,
                f"mutation without {removed} did not fail")


def main() -> None:
    tests = [value for name, value in sorted(globals().items()) if name.startswith("test_") and callable(value)]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    print(f"PASS {len(tests)} focused policy tests")


if __name__ == "__main__":
    main()
