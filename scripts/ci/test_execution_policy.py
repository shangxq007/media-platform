#!/usr/bin/env python3
"""Fail-closed static guard for TEST_EXECUTION_PARALLELIZATION_AND_VALIDATION_POLICY_V1."""

from __future__ import annotations

import argparse
import csv
import re
from dataclasses import dataclass
from pathlib import Path


TOPOLOGY_LEDGER = Path("governance/TEST_EXECUTION_TOPOLOGY_LEDGER.tsv")
SERIAL_LEDGER = Path("governance/TEST_EXECUTION_SERIAL_ONLY_LEDGER.tsv")
CI_GRAPH = Path("governance/CI_VALIDATION_DEPENDENCY_GRAPH.tsv")
NEXT_DEBT_PLAN = Path("governance/NEXT_DEBT_PARALLELIZATION_PLAN.tsv")
DECLARED_OVERLAPS = Path("governance/TEST_EXECUTION_DECLARED_OVERLAPS.tsv")
ACCOUNTING_BASELINE = Path("governance/TEST_EXECUTION_ACCOUNTING_BASELINE.tsv")
CANDIDATE_ENROLLMENT = Path("governance/TEST_EXECUTION_CANDIDATE_ENROLLMENT.tsv")
PROMOTION_RECEIPT_PATH = Path("governance/TEST_EXECUTION_PARALLELIZATION_PROMOTION_RECEIPT_V1.tsv")
POLICY_LEVELS = ("V0_PRE_COMMIT", "V1_IMPLEMENTATION", "V2_CANDIDATE_FCV", "V3_CHECKPOINT")
REQUIRED_LEDGER_COLUMNS = (
    "PROJECT", "TEST_TASK", "SOURCE_SET", "DISCOVERED_TEST_COUNT", "USES_SPRING_CONTEXT",
    "USES_SPRING_BOOT_TEST", "USES_MODULITH", "USES_TESTCONTAINERS", "USES_DATABASE",
    "USES_TEMPORAL", "USES_DOCKER", "USES_FIXED_PORT", "USES_FILESYSTEM_GLOBAL_STATE",
    "USES_MUTABLE_STATIC_STATE", "USES_SYSTEM_PROPERTY_MUTATION", "USES_ENVIRONMENT_MUTATION",
    "USES_DIRTIES_CONTEXT", "KNOWN_SHARED_EXTERNAL_RESOURCE", "CURRENT_MAX_PARALLEL_FORKS",
    "CURRENT_HEAP_LIMIT", "CLASSIFICATION", "CLASSIFICATION_REASON",
)
REQUIRED_SERIAL_COLUMNS = ("PROJECT", "TEST_TASK", "SERIAL_REASON", "OWNER_REVIEW_REQUIRED")
REQUIRED_CI_GRAPH_COLUMNS = ("LANE", "DEPENDS_ON", "SHARED_OUTPUTS", "CAN_RUN_IN_PARALLEL", "CONFLICT_REASON")
REQUIRED_DEBT_COLUMNS = (
    "EPOCH", "MUST_WAIT_FOR", "CAN_RUN_IN_PARALLEL_WITH", "CONFLICTING_EPOCHS",
    "SHARED_AUTHORITY_CONFLICT", "SHARED_FILE_CONFLICT", "SAFE_ENGINEERING_LANE", "RECOMMENDED_PUBLICATION_ORDER",
)
REQUIRED_OVERLAP_COLUMNS = (
    "PROJECT", "TEST_TASK", "OVERLAPS_WITH_TASK", "DUPLICATE_TEST_COUNT", "SELECTION_MECHANISM", "RATIONALE",
)
REQUIRED_ACCOUNTING_BASELINE_COLUMNS = ("METRIC", "VALUE", "DESCRIPTION")
REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS = (
    "PROJECT", "TEST_TASK", "PROPOSED_CLASSIFICATION", "BENCHMARK_EVIDENCE_REFERENCE",
    "WINNER_REPLAY_EVIDENCE_REFERENCE", "PROMOTION_STATUS",
)
PROMOTION_RECEIPT_COLUMNS = ("FIELD", "VALUE")
PROMOTION_RECEIPT_REFERENCE = PROMOTION_RECEIPT_PATH.as_posix()
_MATRIX_WALL_SECONDS = {1: "1910.394", 8: "1637.392", 16: "1346.504", 24: "1331.304", 32: "1596.930"}
_WINNER_REPLAY_WALL_SECONDS = ("1496.555", "1548.576", "1589.338")


def _expected_promotion_receipt() -> dict[str, str]:
    expected = {
        "RECEIPT_VERSION": "TEST_EXECUTION_PARALLELIZATION_PROMOTION_RECEIPT_V1",
        "POLICY": "TEST_EXECUTION_PARALLELIZATION_AND_VALIDATION_POLICY_V1",
        "MATRIX_TSV_FILENAME": "test-execution-parallelization-v1-matrix.tsv",
        "MATRIX_TSV_SHA256": "c2c18d0c599a24c97ced2aeefa6818b825195e08f203e74ed0538afcaa3cc397",
        "WINNER_REPLAY_TSV_FILENAME": "test-execution-parallelization-v1-winner-replay.tsv",
        "WINNER_REPLAY_TSV_SHA256": "7b128a1d236fa03f1d23101f89e4471cd4f7c4f0e2d105560331d1ec48d3f17c",
        "MATRIX_RESULT_COUNT": "5",
        "WINNER_REPLAY_RESULT_COUNT": "3",
        "WINNER_WORKERS": "24",
        "PURE_FORKS": "2",
        "SPRING_FORKS": "1",
        "SERIAL_FORKS": "1",
        "UNIVERSE": "8209",
        "PASSED": "8180",
        "SKIPPED": "29",
        "FAILURES": "0",
        "ERRORS": "0",
        "RESOURCE_COLLISIONS": "0",
        "FLAKY_FAILURES": "0",
        "ACCOUNTING_STATUS": "PASS",
        "BASELINE_WALL_SECONDS": "2015.000",
        "BEST_WINNER_MATRIX_WALL_SECONDS": "1331.304",
        "BEST_WINNER_MATRIX_SPEEDUP": "1.514",
        "SAVED_SECONDS": "683.696",
        "CORRECTNESS_STATUS": "PASS",
        "THREE_X_TARGET_STATUS": "NOT_MET",
        "OLD_PER_EPOCH_SERIAL_POLICY_STATUS": "STILL_AUTHORITATIVE_UNTIL_THIS_FOUNDATION_IS_PUBLISHED",
    }
    for workers, wall_seconds in _MATRIX_WALL_SECONDS.items():
        prefix = f"MATRIX_WORKERS_{workers}"
        expected.update({
            f"{prefix}_WALL_SECONDS": wall_seconds,
            f"{prefix}_TESTS": "8209",
            f"{prefix}_PASSED": "8180",
            f"{prefix}_SKIPPED": "29",
            f"{prefix}_FAILED": "0",
            f"{prefix}_ERRORS": "0",
            f"{prefix}_RESOURCE_COLLISIONS": "0",
            f"{prefix}_FLAKY_FAILURES": "0",
            f"{prefix}_ACCOUNTING_STATUS": "PASS",
        })
    for replay, wall_seconds in enumerate(_WINNER_REPLAY_WALL_SECONDS, start=1):
        prefix = f"WINNER_REPLAY_{replay}"
        expected.update({
            f"{prefix}_WORKERS": "24",
            f"{prefix}_WALL_SECONDS": wall_seconds,
            f"{prefix}_TESTS": "8209",
            f"{prefix}_PASSED": "8180",
            f"{prefix}_SKIPPED": "29",
            f"{prefix}_FAILED": "0",
            f"{prefix}_ERRORS": "0",
            f"{prefix}_RESOURCE_COLLISIONS": "0",
            f"{prefix}_FLAKY_FAILURES": "0",
            f"{prefix}_ACCOUNTING_STATUS": "PASS",
        })
    return expected


EXPECTED_PROMOTION_RECEIPT = _expected_promotion_receipt()
ORIGINAL_CANDIDATE_PROFILE_KEYS = frozenset({
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
})


@dataclass(frozen=True)
class PolicyReport:
    task_count: int
    serial_task_count: int
    pure_task_count: int


def read_tsv(path: Path, columns: tuple[str, ...]) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            actual = tuple(reader.fieldnames or ())
            if actual != columns:
                raise ValueError(f"{path}: required header {columns}, got {actual}")
            rows = list(reader)
    except FileNotFoundError as exc:
        raise ValueError(f"Missing required policy artifact: {path}") from exc
    if not rows:
        raise ValueError(f"{path}: must not be empty")
    if any(set(row) != set(columns) or any(value is None for value in row.values()) for row in rows):
        raise ValueError(f"{path}: malformed TSV row")
    return rows


def read_promotion_receipt(path: Path) -> dict[str, str]:
    rows = read_tsv(path, PROMOTION_RECEIPT_COLUMNS)
    receipt = {row["FIELD"]: row["VALUE"] for row in rows}
    if len(receipt) != len(rows) or any(not field.strip() or not value.strip() for field, value in receipt.items()):
        raise ValueError(f"{path}: promotion receipt has duplicate or blank fields")
    return receipt


def validate_promotion_receipt(root: Path) -> dict[str, str]:
    receipt = read_promotion_receipt(root / PROMOTION_RECEIPT_PATH)
    if receipt != EXPECTED_PROMOTION_RECEIPT:
        missing = sorted(set(EXPECTED_PROMOTION_RECEIPT) - set(receipt))
        extra = sorted(set(receipt) - set(EXPECTED_PROMOTION_RECEIPT))
        changed = sorted(
            field for field in set(receipt) & set(EXPECTED_PROMOTION_RECEIPT)
            if receipt[field] != EXPECTED_PROMOTION_RECEIPT[field]
        )
        raise ValueError(
            "promotion receipt is not the exact Hermes-controlled result: "
            f"missing={missing}, extra={extra}, changed={changed}"
        )
    return receipt


def declared_test_tasks(root: Path) -> set[tuple[str, str]]:
    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    active = settings.split("// ── HOLD", 1)[0]
    modules = re.findall(r'^\s*"([^"]+)",?\s*$', active, flags=re.MULTILINE)
    tasks = {(f":{module}", "test") for module in modules}
    tasks.update({
        (":platform-algorithms", "test"),
        (":platform-algorithms:graph", "test"),
    })
    tasks.update({
        (":sandbox-isolation-module", "phase17SandboxConformanceTest"),
        (":platform-app", "phase17SandboxConformanceTest"),
        (":platform-app", "renderIntegrationTest"),
    })
    return tasks


def validate_candidate_enrollment(
    topology_by_key: dict[tuple[str, str], dict[str, str]], enrollment: list[dict[str, str]],
) -> None:
    candidate_keys = [(row["PROJECT"], row["TEST_TASK"]) for row in enrollment]
    if len(candidate_keys) != len(set(candidate_keys)):
        raise ValueError("candidate enrollment contains duplicate task rows")
    if set(candidate_keys) != ORIGINAL_CANDIDATE_PROFILE_KEYS:
        raise ValueError(
            "candidate enrollment must exactly match the original 21-task pure census: "
            f"missing={sorted(ORIGINAL_CANDIDATE_PROFILE_KEYS - set(candidate_keys))}, "
            f"extra={sorted(set(candidate_keys) - ORIGINAL_CANDIDATE_PROFILE_KEYS)}"
        )
    for candidate in enrollment:
        key = (candidate["PROJECT"], candidate["TEST_TASK"])
        if key not in topology_by_key or candidate["PROPOSED_CLASSIFICATION"] != "PURE_PARALLEL_SAFE":
            raise ValueError(f"invalid candidate enrollment: {key}")
        status = candidate["PROMOTION_STATUS"]
        benchmark = candidate["BENCHMARK_EVIDENCE_REFERENCE"]
        replay = candidate["WINNER_REPLAY_EVIDENCE_REFERENCE"]
        classification = topology_by_key[key]["CLASSIFICATION"]
        if status == "CANDIDATE_ONLY":
            if (benchmark, replay) != ("PENDING_BENCHMARK", "PENDING_WINNER_REPLAY"):
                raise ValueError(f"candidate-only enrollment has non-pending evidence: {key}")
            if classification != "REVIEW_REQUIRED":
                raise ValueError(f"candidate-only enrollment was canonically promoted: {key}")
        elif status == "SEALED_PROMOTION":
            if classification != "PURE_PARALLEL_SAFE":
                raise ValueError(f"sealed promotion lacks canonical PURE_PARALLEL_SAFE topology: {key}")
            if (benchmark, replay) != (PROMOTION_RECEIPT_REFERENCE, PROMOTION_RECEIPT_REFERENCE):
                raise ValueError(f"sealed promotion lacks the tracked exact promotion receipt: {key}")
        else:
            raise ValueError(f"unknown candidate promotion status: {key}: {status}")


def validate_repository(root: Path) -> PolicyReport:
    topology = read_tsv(root / TOPOLOGY_LEDGER, REQUIRED_LEDGER_COLUMNS)
    serial = read_tsv(root / SERIAL_LEDGER, REQUIRED_SERIAL_COLUMNS)
    graph = read_tsv(root / CI_GRAPH, REQUIRED_CI_GRAPH_COLUMNS)
    debt = read_tsv(root / NEXT_DEBT_PLAN, REQUIRED_DEBT_COLUMNS)
    overlaps = read_tsv(root / DECLARED_OVERLAPS, REQUIRED_OVERLAP_COLUMNS)
    baseline = read_tsv(root / ACCOUNTING_BASELINE, REQUIRED_ACCOUNTING_BASELINE_COLUMNS)
    enrollment = read_tsv(root / CANDIDATE_ENROLLMENT, REQUIRED_CANDIDATE_ENROLLMENT_COLUMNS)
    validate_promotion_receipt(root)
    del debt

    keys = [(row["PROJECT"], row["TEST_TASK"]) for row in topology]
    if len(keys) != len(set(keys)):
        raise ValueError("topology ledger contains duplicate Test task rows")
    expected = declared_test_tasks(root)
    actual = set(keys)
    if actual != expected:
        raise ValueError(f"topology ledger universe mismatch: missing={sorted(expected - actual)}, extra={sorted(actual - expected)}")
    if len(actual) != 51:
        raise ValueError(f"expected Phase A 51 Test tasks, got {len(actual)}")

    serial_keys: set[tuple[str, str]] = set()
    topology_by_key = {(row["PROJECT"], row["TEST_TASK"]): row for row in topology}
    for row in serial:
        key = (row["PROJECT"], row["TEST_TASK"])
        if key in serial_keys:
            raise ValueError(f"duplicate serial-only row: {key}")
        if not row["SERIAL_REASON"].strip():
            raise ValueError(f"blank serial reason: {key}")
        if row["OWNER_REVIEW_REQUIRED"] != "YES":
            raise ValueError(f"serial-only row lacks owner review marker: {key}")
        if key not in topology_by_key or row["SERIAL_REASON"] != topology_by_key[key]["CLASSIFICATION_REASON"]:
            raise ValueError(f"serial-only reason does not exactly match topology classification reason: {key}")
        serial_keys.add(key)

    pure_count = 0
    serial_count = 0
    for row in topology:
        key = (row["PROJECT"], row["TEST_TASK"])
        classification = row["CLASSIFICATION"]
        try:
            forks = int(row["CURRENT_MAX_PARALLEL_FORKS"])
            discovered = int(row["DISCOVERED_TEST_COUNT"])
        except ValueError as exc:
            raise ValueError(f"non-integer count/fork for {key}") from exc
        if discovered < 0 or forks not in (1, 2):
            raise ValueError(f"invalid discovered count or conservative fork bound for {key}")
        if not row["CLASSIFICATION_REASON"].strip():
            raise ValueError(f"blank classification reason for {key}")
        if classification not in {"PURE_PARALLEL_SAFE", "SPRING_HEAVY_BOUNDED", "REVIEW_REQUIRED", "SERIAL_ONLY"}:
            raise ValueError(f"unknown classification for {key}: {classification}")
        if classification == "PURE_PARALLEL_SAFE":
            pure_count += 1
            enrolled = [candidate for candidate in enrollment if (candidate["PROJECT"], candidate["TEST_TASK"]) == key]
            if forks != 2 or len(enrolled) != 1 or enrolled[0]["PROMOTION_STATUS"] != "SEALED_PROMOTION":
                raise ValueError(f"PURE_PARALLEL_SAFE task lacks sealed affirmative evidence: {key}")
            if any(enrolled[0][column] != PROMOTION_RECEIPT_REFERENCE for column in
                   ("BENCHMARK_EVIDENCE_REFERENCE", "WINNER_REPLAY_EVIDENCE_REFERENCE")):
                raise ValueError(f"PURE_PARALLEL_SAFE task lacks the tracked exact promotion receipt: {key}")
        elif forks != 1:
            raise ValueError(f"non-PURE_PARALLEL_SAFE task exceeds one fork: {key}")
        if classification == "SPRING_HEAVY_BOUNDED" and row["USES_SPRING_CONTEXT"] != "YES":
            raise ValueError(f"SPRING_HEAVY_BOUNDED task lacks a Spring context: {key}")
        if row["USES_SPRING_CONTEXT"] == "YES" and classification not in {"SPRING_HEAVY_BOUNDED", "SERIAL_ONLY"}:
            raise ValueError(f"Spring-context task is neither bounded nor explicitly serial: {key}")
        if classification == "SERIAL_ONLY":
            serial_count += 1
            if key not in serial_keys:
                raise ValueError(f"serial task has no serial-only ledger reason: {key}")
        elif key in serial_keys:
            raise ValueError(f"serial-only ledger task is not SERIAL_ONLY: {key}")

    platform_rows = [row for row in topology if row["PROJECT"] == ":platform-app"]
    if not platform_rows or any(row["CURRENT_MAX_PARALLEL_FORKS"] != "1" for row in platform_rows):
        raise ValueError("platform-app Test tasks must be capped at one fork")
    if not serial_keys:
        raise ValueError("serial-only ledger is empty")
    required_lanes = {"BACKEND", "ARCHITECTURE_STATIC", "FRONTEND", "SEMGREP", "FORMAL_LEAN_COQ", "GITOPS", "RUNTIME_IMAGE_LOCAL_BUILD"}
    graph_lanes = {row["LANE"] for row in graph}
    if not required_lanes.issubset(graph_lanes):
        raise ValueError(f"CI graph missing required lanes: {sorted(required_lanes - graph_lanes)}")
    if any(row["CAN_RUN_IN_PARALLEL"] not in {"YES", "NO"} for row in graph):
        raise ValueError("CI graph has invalid parallel decision")
    if any(not row["CONFLICT_REASON"].strip() for row in graph):
        raise ValueError("CI graph has blank conflict decision")

    baseline_by_metric = {row["METRIC"]: row["VALUE"] for row in baseline}
    expected_baseline = {
        "TOP_LEVEL_EXPECTED_UNIVERSE": "8094", "RAW_RECURSIVE_EXPECTED_UNIVERSE": "8209",
        "EXPECTED_SKIPPED": "29", "TASK_TOPOLOGY_GROSS_COUNT": "8215", "DECLARED_OVERLAP_COUNT": "6",
    }
    if baseline_by_metric != expected_baseline:
        raise ValueError(f"unexpected accounting baseline: {baseline_by_metric}")
    overlap_count = sum(int(row["DUPLICATE_TEST_COUNT"]) for row in overlaps)
    if overlap_count != int(baseline_by_metric["DECLARED_OVERLAP_COUNT"]):
        raise ValueError("declared overlap count does not match baseline")
    if sum(int(row["DISCOVERED_TEST_COUNT"]) for row in topology) != int(baseline_by_metric["TASK_TOPOLOGY_GROSS_COUNT"]):
        raise ValueError("topology gross count does not match baseline")
    if int(baseline_by_metric["TASK_TOPOLOGY_GROSS_COUNT"]) - overlap_count != int(baseline_by_metric["RAW_RECURSIVE_EXPECTED_UNIVERSE"]):
        raise ValueError("topology gross count does not reconcile with declared overlaps")
    validate_candidate_enrollment(topology_by_key, enrollment)

    build = (root / "build.gradle.kts").read_text(encoding="utf-8")
    if "TEST_EXECUTION_PARALLELIZATION_AND_VALIDATION_POLICY_V1" not in build:
        raise ValueError("root Gradle policy marker missing")
    if 'systemProperty("junit.jupiter.execution.parallel.enabled", "false")' not in build:
        raise ValueError("global JUnit intra-JVM parallelism is not explicitly disabled")
    if "maxParallelForks = topology.maxParallelForks" not in build:
        raise ValueError("root Gradle policy does not apply ledger fork limits")
    for required_snippet in ("parallelEligibleTestTasks", "restrictedTestTasks", "mustRunAfter(parallelEligibleTestTasks)", "mustRunAfter(previous)"):
        if required_snippet not in build:
            raise ValueError(f"root Gradle policy lacks restricted scheduling contract: {required_snippet}")
    gradle_sources = [source for pattern in ("*.gradle.kts", "*.gradle", "*.properties")
                      for source in root.rglob(pattern) if source.is_file()]
    forbidden = ("maxParallelForks = 16", "maxParallelForks = 32", "maxParallelForks = 80",
                 "junit.jupiter.execution.parallel.enabled=true", "junit.jupiter.execution.parallel.enabled\", \"true")
    if any(token in source.read_text(encoding="utf-8") for source in gradle_sources for token in forbidden):
        raise ValueError("forbidden global parallelism configuration detected")
    return PolicyReport(len(actual), serial_count, pure_count)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    report = validate_repository(args.root.resolve())
    print(f"PASS test-execution-policy tasks={report.task_count} serial={report.serial_task_count} pure={report.pure_task_count}")


if __name__ == "__main__":
    main()
