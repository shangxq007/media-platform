#!/usr/bin/env python3
"""Positive, mutation, and Git-mode tests for the Phase 19 final evidence guard."""

from __future__ import annotations

import copy
import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any, Callable


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
GUARD = REPOSITORY_ROOT / "scripts/phase19-final-governance-evidence-guard.py"
ARTIFACTS = [
    Path("docs/architecture/governance/roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json"),
    Path("docs/architecture/governance/roadmap-22-phase-19-capability-disposition-reconciliation-v1.json"),
    Path("docs/architecture/governance/roadmap-22-phase-19-test-surface-change-accounting-v1.json"),
    Path("docs/architecture/governance/roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"),
    Path("docs/architecture/governance/roadmap-22-phase-19-render-zero-awareness-clean-forward-path-ledger-v2.json"),
]
CAPABILITY = ARTIFACTS[0]
RECONCILIATION = ARTIFACTS[1]
TEST_ACCOUNTING = ARTIFACTS[2]
SEMGREP = ARTIFACTS[3]
CLEAN_FORWARD = ARTIFACTS[4]
CANDIDATE_PATH = Path("candidate/staged-change.txt")
FALLBACK_UNCOVERED_PATH = Path("candidate/post-commit-uncovered.txt")

EXPECTED_PASS = "\n".join(
    [
        "CAPABILITY_DISPOSITION_RECONCILIATION=PASS",
        "CAPABILITY_DISPOSITION_RECONCILIATION_DELTA=0",
        "DUPLICATE_PRIMARY_DISPOSITION_COUNT=0",
        "UNACCOUNTED_CAPABILITY_COUNT=0",
        "UNEXPLAINED_TEST_COUNT_REDUCTION=0",
        "UNEXPLAINED_BEHAVIORAL_TEST_LOSS=0",
        "SEMGREP_TARGET_DELTA_EXPLAINED=YES",
        "CLEAN_FORWARD_LEDGER_VALIDATION=PASS",
        "FINAL_GOVERNANCE_EVIDENCE_GATE=PASS",
        "",
    ]
)


class TestFailure(Exception):
    pass


def load_guard_module() -> Any:
    spec = importlib.util.spec_from_file_location("phase19_final_governance_evidence_guard", GUARD)
    if spec is None or spec.loader is None:
        raise TestFailure("cannot load guard module")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


GUARD_MODULE = load_guard_module()


class Fixture:
    """Copied evidence plus an isolated repository with a staged candidate."""

    def __init__(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix="phase19-final-evidence-")
        self.root = Path(self._temporary.name)
        self.historical_identity: tuple[str, str] | None = None
        for relative in ARTIFACTS:
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPOSITORY_ROOT / relative, destination)

        self.git("init", "-q")
        self.git("config", "user.name", "Phase19 Guard Test")
        self.git("config", "user.email", "phase19-guard-test@example.invalid")
        (self.root / ".fixture-base").write_text("base\n", encoding="utf-8")
        self.git("add", ".fixture-base", *(str(path) for path in ARTIFACTS))
        self.git("commit", "-q", "-m", "fixture base")
        self.base_sha = self.git("rev-parse", "HEAD").stdout.strip()

        clean = self.read(CLEAN_FORWARD)
        initial_entries = [
            copy.deepcopy(entry)
            for entry in clean["paths"]
            if entry.get("initial_finding_path") is True
        ]
        candidate_entry = {
            "path": str(CANDIDATE_PATH),
            "finding_tuple_count": 0,
            "category_counts": {},
            "disposition": "ADD_GOVERNANCE_EVIDENCE",
            "rationale": "isolated candidate-scope fixture",
            "line_count": 0,
            "pattern_ids": [],
            "initial_finding_path": False,
            "supplemental_candidate_scope": True,
            "candidate_status": "A",
        }
        clean["base_sha"] = self.base_sha
        clean["paths"] = [*initial_entries, candidate_entry]
        clean["path_count"] = len(clean["paths"])
        clean["candidate_scope_path_count"] = 1
        clean["supplemental_candidate_scope_path_count"] = 1
        clean["duplicate_path_count"] = 0
        clean["unclassified_count"] = 0
        clean["unexplained_keep_count"] = 0
        clean["candidate_scope_uncovered_count"] = 0
        clean["stale_finding_path_count"] = 0
        self.write(CLEAN_FORWARD, clean)

        candidate = self.root / CANDIDATE_PATH
        candidate.parent.mkdir(parents=True, exist_ok=True)
        candidate.write_text("candidate\n", encoding="utf-8")
        self.git("add", str(CANDIDATE_PATH))

    def close(self) -> None:
        self._temporary.cleanup()

    def __enter__(self) -> "Fixture":
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        self.close()

    def git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=self.root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if completed.returncode != 0:
            raise TestFailure(f"fixture Git failed: git {' '.join(arguments)}: {completed.stderr.strip()}")
        return completed

    def read(self, relative: Path) -> dict[str, Any]:
        with (self.root / relative).open("r", encoding="utf-8") as stream:
            value = json.load(stream)
        if not isinstance(value, dict):
            raise TestFailure(f"fixture artifact is not an object: {relative}")
        return value

    def write(self, relative: Path, value: dict[str, Any]) -> None:
        with (self.root / relative).open("w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write("\n")

    def run_guard(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(GUARD), "--root", str(self.root), *arguments],
            cwd=self.root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def run_historical_guard(self, reviewed_sha: str, reviewed_tree: str) -> subprocess.CompletedProcess[str]:
        arguments = [str(GUARD), "validate", str(self.root), reviewed_sha, reviewed_tree]
        try:
            GUARD_MODULE.validate(self.root, reviewed_sha, reviewed_tree)
        except (GUARD_MODULE.GuardError, OSError, RuntimeError) as exc:
            return subprocess.CompletedProcess(
                arguments,
                1,
                "",
                f"FINAL_GOVERNANCE_EVIDENCE_GATE=FAIL: {exc}\n",
            )
        return subprocess.CompletedProcess(arguments, 0, EXPECTED_PASS, "")

    def commit_candidate_for_fallback(self) -> tuple[str, str]:
        clean = self.read(CLEAN_FORWARD)
        clean["paths"].append(
            {
                "path": str(CLEAN_FORWARD),
                "finding_tuple_count": 0,
                "category_counts": {},
                "disposition": "ADD_GOVERNANCE_EVIDENCE",
                "rationale": "isolated historical candidate-scope fixture",
                "line_count": 0,
                "pattern_ids": [],
                "initial_finding_path": False,
                "supplemental_candidate_scope": True,
                "candidate_status": "M",
            }
        )
        clean["path_count"] = len(clean["paths"])
        clean["candidate_scope_path_count"] = 2
        clean["supplemental_candidate_scope_path_count"] = 2
        self.write(CLEAN_FORWARD, clean)
        self.git("add", str(CLEAN_FORWARD))
        self.git("commit", "-q", "-m", "candidate commit")
        staged = self.git("diff", "--cached", "--name-only").stdout
        if staged:
            raise TestFailure("post-commit fallback fixture still has staged paths")
        reviewed_sha = self.git("rev-parse", "HEAD").stdout.strip()
        reviewed_tree = self.git("rev-parse", "HEAD^{tree}").stdout.strip()
        self.historical_identity = (reviewed_sha, reviewed_tree)
        return reviewed_sha, reviewed_tree

    def add_descendant_commit(self) -> None:
        (self.root / ".fixture-descendant").write_text("descendant\n", encoding="utf-8")
        self.git("add", ".fixture-descendant")
        self.git("commit", "-q", "-m", "descendant commit")


Mutation = Callable[[Fixture], None]


def mutate_json(relative: Path, mutation: Callable[[dict[str, Any]], None]) -> Mutation:
    def apply(fixture: Fixture) -> None:
        data = fixture.read(relative)
        mutation(data)
        fixture.write(relative, data)

    return apply


def assert_pass(name: str, fixture: Fixture, *arguments: str) -> None:
    completed = fixture.run_guard(*arguments)
    if completed.returncode != 0 or completed.stdout != EXPECTED_PASS or completed.stderr:
        raise TestFailure(
            f"{name} did not pass exactly; rc={completed.returncode}; "
            f"stdout={completed.stdout!r}; stderr={completed.stderr!r}"
        )


def assert_historical_pass(name: str, fixture: Fixture, reviewed_sha: str, reviewed_tree: str) -> None:
    completed = fixture.run_historical_guard(reviewed_sha, reviewed_tree)
    if completed.returncode != 0 or completed.stdout != EXPECTED_PASS or completed.stderr:
        raise TestFailure(
            f"{name} did not pass exactly; rc={completed.returncode}; "
            f"stdout={completed.stdout!r}; stderr={completed.stderr!r}"
        )


def assert_historical_guard_fails(
    name: str,
    fixture: Fixture,
    expected: str,
    reviewed_sha: str,
    reviewed_tree: str,
) -> None:
    completed = fixture.run_historical_guard(reviewed_sha, reviewed_tree)
    if completed.returncode == 0:
        raise TestFailure(f"guard unexpectedly passed: {name}")
    if "FINAL_GOVERNANCE_EVIDENCE_GATE=FAIL:" not in completed.stderr:
        raise TestFailure(f"guard did not fail closed: {name}: {completed.stderr!r}")
    if expected not in completed.stderr:
        raise TestFailure(
            f"guard failed for the wrong reason: {name}; "
            f"expected={expected!r}; stderr={completed.stderr!r}"
        )


def assert_cli_authority_overrides_rejected() -> None:
    with Fixture() as fixture:
        completed = fixture.run_guard(
            "--historical-evidence-sha",
            "0" * 40,
            "--historical-evidence-tree",
            "f" * 40,
        )
        if completed.returncode == 0:
            raise TestFailure("public historical evidence authority overrides unexpectedly accepted")
        for option in ("--historical-evidence-sha", "--historical-evidence-tree"):
            if option not in completed.stderr:
                raise TestFailure(f"CLI did not reject public authority override: {option}")


def assert_clean_production_descendant_passes() -> None:
    with tempfile.TemporaryDirectory(prefix="phase19-production-descendant-") as temporary:
        checkout = Path(temporary) / "checkout"
        completed = subprocess.run(
            ["git", "clone", "-q", "--no-hardlinks", str(REPOSITORY_ROOT), str(checkout)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if completed.returncode != 0:
            raise TestFailure(f"clean descendant clone failed: {completed.stderr.strip()}")
        completed = subprocess.run(
            [sys.executable, str(GUARD), "--root", str(checkout)],
            cwd=checkout,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if completed.returncode != 0 or completed.stdout != EXPECTED_PASS or completed.stderr:
            raise TestFailure(
                "clean production descendant did not pass exactly; "
                f"rc={completed.returncode}; stdout={completed.stdout!r}; stderr={completed.stderr!r}"
            )


def assert_mutation_fails(name: str, mutation: Mutation) -> None:
    with Fixture() as fixture:
        mutation(fixture)
        if fixture.historical_identity is None:
            completed = fixture.run_guard()
        else:
            completed = fixture.run_historical_guard(*fixture.historical_identity)
        if completed.returncode == 0:
            raise TestFailure(f"mutation unexpectedly passed: {name}")
        if "FINAL_GOVERNANCE_EVIDENCE_GATE=FAIL:" not in completed.stderr:
            raise TestFailure(f"mutation did not fail closed with guard output: {name}: {completed.stderr!r}")


def find_capability(data: dict[str, Any], key: str) -> dict[str, Any]:
    for row in data["capabilities"]:
        if row.get("CapabilityKey") == key:
            return row
    raise TestFailure(f"missing fixture capability: {key}")


def duplicate_capability_key(data: dict[str, Any]) -> None:
    data["capabilities"][1]["CapabilityKey"] = data["capabilities"][0]["CapabilityKey"]


def missing_disposition(data: dict[str, Any]) -> None:
    del data["capabilities"][0]["Disposition"]


def invalid_disposition(data: dict[str, Any]) -> None:
    data["capabilities"][0]["Disposition"] = "NOT_A_DISPOSITION"


def exact_capability_wrong(data: dict[str, Any]) -> None:
    find_capability(data, "HARDCODED_DURATION_REPORTING")["Disposition"] = "DELETED_AS_PROVEN_DUPLICATE"


def reconciliation_count_mismatch(data: dict[str, Any]) -> None:
    data["primary_disposition_counts"]["MIGRATED_TO_FFMPEG_PROVIDER"] += 1


def reconciliation_key_mismatch(data: dict[str, Any]) -> None:
    data["capabilities"][0]["CapabilityKey"] = "WRONG_CAPABILITY_KEY"


def reconciliation_list_mismatch(data: dict[str, Any]) -> None:
    data["primary_disposition_order"] = list(reversed(data["primary_disposition_order"]))


def test_arithmetic_mismatch(data: dict[str, Any]) -> None:
    first_key = next(iter(data["added_class_counts"]))
    data["added_class_counts"][first_key] += 1


def unexplained_behavioral_loss(data: dict[str, Any]) -> None:
    data["unexplained_behavioral_test_loss"] = 1


def duplicate_deleted_group_class(data: dict[str, Any]) -> None:
    groups = list(data["deleted_test_class_groups"].values())
    groups[1]["classes"].append(groups[0]["classes"][0])


def semgrep_net_mismatch(data: dict[str, Any]) -> None:
    data["removed_plus_added_net"] = -24


def semgrep_error(data: dict[str, Any]) -> None:
    data["candidate_errors"] = 1


def semgrep_finding(data: dict[str, Any]) -> None:
    data["base_findings"] = 1


def clean_duplicate(data: dict[str, Any]) -> None:
    data["paths"].append(copy.deepcopy(data["paths"][0]))
    data["path_count"] += 1
    data["duplicate_path_count"] = 1


def clean_unclassified(data: dict[str, Any]) -> None:
    data["paths"][-1]["disposition"] = ""
    data["unclassified_count"] = 1


def clean_keep(data: dict[str, Any]) -> None:
    data["paths"][-1]["disposition"] = "KEEP"
    data["unexplained_keep_count"] = 1


def clean_stale(data: dict[str, Any]) -> None:
    data["paths"].append(
        {
            "path": "candidate/stale-ledger-entry.txt",
            "finding_tuple_count": 0,
            "category_counts": {},
            "disposition": "ADD_GOVERNANCE_EVIDENCE",
            "rationale": "negative-control stale path",
            "line_count": 0,
            "pattern_ids": [],
            "initial_finding_path": False,
            "candidate_status": "A",
        }
    )
    data["path_count"] += 1
    data["stale_finding_path_count"] = 1


def clean_uncovered(fixture: Fixture) -> None:
    path = fixture.root / "candidate/uncovered-staged-path.txt"
    path.write_text("uncovered\n", encoding="utf-8")
    fixture.git("add", "candidate/uncovered-staged-path.txt")


def malformed_input(fixture: Fixture) -> None:
    (fixture.root / TEST_ACCOUNTING).write_text("{ malformed", encoding="utf-8")


def missing_input(fixture: Fixture) -> None:
    (fixture.root / SEMGREP).unlink()


def fallback_uncovered(fixture: Fixture) -> None:
    path = fixture.root / FALLBACK_UNCOVERED_PATH
    path.write_text("fallback uncovered\n", encoding="utf-8")
    fixture.git("add", str(FALLBACK_UNCOVERED_PATH))
    fixture.commit_candidate_for_fallback()


def main() -> int:
    compile(GUARD.read_bytes(), str(GUARD), "exec")
    compile(Path(__file__).read_bytes(), str(Path(__file__)), "exec")

    with Fixture() as fixture:
        assert_pass("staged-index scope mode", fixture)

    assert_cli_authority_overrides_rejected()
    assert_clean_production_descendant_passes()

    with Fixture() as fixture:
        reviewed_sha, reviewed_tree = fixture.commit_candidate_for_fallback()
        assert_historical_pass(
            "reviewed SHA equal to current HEAD",
            fixture,
            reviewed_sha,
            reviewed_tree,
        )

    with Fixture() as fixture:
        reviewed_sha, reviewed_tree = fixture.commit_candidate_for_fallback()
        fixture.add_descendant_commit()
        assert_historical_pass(
            "reviewed SHA is an ancestor of current HEAD",
            fixture,
            reviewed_sha,
            reviewed_tree,
        )

    with Fixture() as fixture:
        reviewed_sha, reviewed_tree = fixture.commit_candidate_for_fallback()
        diverged_sha = fixture.git(
            "commit-tree", reviewed_tree, "-p", fixture.base_sha, "-m", "diverged candidate"
        ).stdout.strip()
        fixture.git("branch", "fixture-diverged-local", diverged_sha)
        branch_sha = fixture.git("rev-parse", "--verify", "refs/heads/fixture-diverged-local").stdout.strip()
        if branch_sha != diverged_sha:
            raise TestFailure("diverged local branch does not point at the reviewed fixture commit")
        assert_historical_guard_fails(
            "reviewed SHA is unreachable from current HEAD",
            fixture,
            "reviewed evidence commit is not an ancestor of current HEAD",
            diverged_sha,
            reviewed_tree,
        )

    with Fixture() as fixture:
        _, reviewed_tree = fixture.commit_candidate_for_fallback()
        assert_historical_guard_fails(
            "reviewed SHA is unknown",
            fixture,
            "reviewed evidence commit is unknown or is not a commit",
            "0" * 40,
            reviewed_tree,
        )

    with Fixture() as fixture:
        _, reviewed_tree = fixture.commit_candidate_for_fallback()
        assert_historical_guard_fails(
            "reviewed SHA is malformed",
            fixture,
            "historical evidence SHA must be a 40-character lowercase hexadecimal object ID",
            "not-a-sha",
            reviewed_tree,
        )

    with Fixture() as fixture:
        reviewed_sha, _ = fixture.commit_candidate_for_fallback()
        assert_historical_guard_fails(
            "reviewed tree is malformed",
            fixture,
            "historical evidence tree must be a 40-character lowercase hexadecimal object ID",
            reviewed_sha,
            "not-a-tree",
        )

    with Fixture() as fixture:
        reviewed_sha, reviewed_tree = fixture.commit_candidate_for_fallback()
        (fixture.root / ".fixture-base").write_text("dirty\n", encoding="utf-8")
        assert_historical_guard_fails(
            "dirty repository in historical mode",
            fixture,
            "historical evidence validation requires a clean repository",
            reviewed_sha,
            reviewed_tree,
        )

    with Fixture() as fixture:
        reviewed_sha, _ = fixture.commit_candidate_for_fallback()
        assert_historical_guard_fails(
            "correct ancestor SHA with wrong tree",
            fixture,
            "reviewed evidence tree mismatch",
            reviewed_sha,
            "f" * 40,
        )

    mutations: list[tuple[str, Mutation]] = [
        ("duplicate CapabilityKey", mutate_json(CAPABILITY, duplicate_capability_key)),
        ("missing disposition", mutate_json(CAPABILITY, missing_disposition)),
        ("invalid disposition", mutate_json(CAPABILITY, invalid_disposition)),
        ("reconciliation count mismatch", mutate_json(RECONCILIATION, reconciliation_count_mismatch)),
        ("reconciliation key mismatch", mutate_json(RECONCILIATION, reconciliation_key_mismatch)),
        ("reconciliation list mismatch", mutate_json(RECONCILIATION, reconciliation_list_mismatch)),
        ("one of four exact capabilities wrong", mutate_json(CAPABILITY, exact_capability_wrong)),
        ("test arithmetic mismatch", mutate_json(TEST_ACCOUNTING, test_arithmetic_mismatch)),
        ("unexplained behavioral loss", mutate_json(TEST_ACCOUNTING, unexplained_behavioral_loss)),
        ("duplicate deleted-group class", mutate_json(TEST_ACCOUNTING, duplicate_deleted_group_class)),
        ("Semgrep net mismatch", mutate_json(SEMGREP, semgrep_net_mismatch)),
        ("Semgrep error", mutate_json(SEMGREP, semgrep_error)),
        ("Semgrep nonzero finding", mutate_json(SEMGREP, semgrep_finding)),
        ("CLEAN FORWARD duplicate path", mutate_json(CLEAN_FORWARD, clean_duplicate)),
        ("CLEAN FORWARD unclassified path", mutate_json(CLEAN_FORWARD, clean_unclassified)),
        ("CLEAN FORWARD unexplained KEEP", mutate_json(CLEAN_FORWARD, clean_keep)),
        ("CLEAN FORWARD uncovered staged path", clean_uncovered),
        ("CLEAN FORWARD stale path", mutate_json(CLEAN_FORWARD, clean_stale)),
        ("malformed input", malformed_input),
        ("missing input", missing_input),
        ("post-commit fallback uncovered path", fallback_uncovered),
    ]
    for name, mutation in mutations:
        assert_mutation_fails(name, mutation)

    print(f"FINAL_GOVERNANCE_EVIDENCE_MUTATION_COUNT={len(mutations)}")
    print("FINAL_GOVERNANCE_EVIDENCE_NEGATIVE_CONTROL=PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except TestFailure as exc:
        print(f"FINAL_GOVERNANCE_EVIDENCE_NEGATIVE_CONTROL=FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
