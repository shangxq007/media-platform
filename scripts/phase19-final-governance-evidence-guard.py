#!/usr/bin/env python3
"""Fail-closed validator for the Roadmap 22 Phase 19 final evidence set."""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, NoReturn


CAPABILITY_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json"
)
RECONCILIATION = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-capability-disposition-reconciliation-v1.json"
)
TEST_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-test-surface-change-accounting-v1.json"
)
SEMGREP_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"
)
CLEAN_FORWARD_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-render-zero-awareness-clean-forward-path-ledger-v2.json"
)
HISTORICAL_EVIDENCE_SHA = "989ee911341157570220837f326c886c4ab2163b"
HISTORICAL_EVIDENCE_TREE = "8f80089e7ee40c5a065f38c56f0a6cb1a517d0d1"
CANONICAL_LINEAGE_ANCHOR_SHA = "a52b3b1c67ce049dc7c500d7f38f49efd386267d"
CANONICAL_LINEAGE_ANCHOR_TREE = "4c76dfcf3108a2dcc9bf0cdc525ade79b86229c5"

# GRD-I01 invariants:
# DESCENDANT_SAFE_HISTORICAL_EVIDENCE_VALIDATION
# AUTHORIZED_CANONICAL_LINEAGE_ANCHORING
# CURRENT_HEAD_ONLY_HISTORICAL_SCOPE=FORBIDDEN
# ARBITRARY_PRE_ANCHOR_LOCAL_DESCENDANT=FORBIDDEN
# BRANCH_NAME_AS_CANONICAL_AUTHORITY=FORBIDDEN
# PUBLIC_IDENTITY_OVERRIDE=FORBIDDEN

DISPOSITIONS = [
    "MIGRATED_TO_FFMPEG_PROVIDER",
    "MIGRATED_TO_PROVIDER_RUNTIME_INFRASTRUCTURE",
    "MIGRATED_TO_PROVIDER_CONFORMANCE",
    "RETAINED_AS_PROVIDER_NEUTRAL_RENDER_SEMANTIC",
    "REPLACED_BY_NEW_ARCHITECTURE",
    "DELETED_AS_PROVEN_DEAD",
    "DELETED_AS_PROVEN_DUPLICATE",
    "DEFERRED_WITH_EXPLICIT_BOUNDED_JUSTIFICATION",
    "MISSING",
]
SUPPORTED_DISPOSITIONS = frozenset(DISPOSITIONS[:5])
EXACT_NON_SUPPORTED = {
    "HARDCODED_DURATION_REPORTING": "DELETED_AS_PROVEN_DEAD",
    "PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT": "DELETED_AS_PROVEN_DEAD",
    "DUPLICATE_RENDER_PROBE_STACK": "DELETED_AS_PROVEN_DUPLICATE",
    "SHADOW_LOCAL_RENDER_RUNNER": "DELETED_AS_PROVEN_DUPLICATE",
}
CLEAN_FORWARD_DISPOSITIONS = frozenset(
    {
        "ADD_FAIL_CLOSED_GUARD",
        "ADD_GOVERNANCE_EVIDENCE",
        "ADD_PROVIDER_NEUTRAL_REGRESSION_TEST",
        "ADD_PROVIDER_NEUTRAL_RENAME_TARGET",
        "DELETE",
        "DELETE_SUPPLEMENTAL_CONCRETE_OR_SHADOW_SURFACE",
        "PROVIDER_NEUTRALIZE",
        "PROVIDER_NEUTRALIZE_SUPPLEMENTAL_SCOPE",
        "RECLASSIFIED_PROVIDER_NEUTRAL_NON_FFMPEG",
    }
)

PASS_OUTPUT = "\n".join(
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
    ]
)


class GuardError(Exception):
    """A deterministic, user-facing validation failure."""


def fail(message: str) -> NoReturn:
    raise GuardError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_int(value: Any, label: str, minimum: int | None = None) -> int:
    require(type(value) is int, f"{label} must be an integer")
    if minimum is not None:
        require(value >= minimum, f"{label} must be >= {minimum}")
    return value


def require_dict(value: Any, label: str) -> dict[str, Any]:
    require(type(value) is dict, f"{label} must be an object")
    return value


def require_list(value: Any, label: str) -> list[Any]:
    require(type(value) is list, f"{label} must be an array")
    return value


def require_string(value: Any, label: str) -> str:
    require(type(value) is str and bool(value), f"{label} must be a non-empty string")
    return value


def strict_equal(actual: Any, expected: Any, label: str) -> None:
    """Compare recursively without Python's bool/int or int/float coercions."""
    require(type(actual) is type(expected), f"{label} has the wrong JSON type")
    if type(expected) is dict:
        actual_keys = set(actual)
        expected_keys = set(expected)
        require(actual_keys == expected_keys, f"{label} has mismatched object keys")
        for key in expected:
            strict_equal(actual[key], expected[key], f"{label}.{key}")
    elif type(expected) is list:
        require(len(actual) == len(expected), f"{label} has mismatched list length")
        for index, expected_item in enumerate(expected):
            strict_equal(actual[index], expected_item, f"{label}[{index}]")
    else:
        require(actual == expected, f"{label} mismatch")


def _object_without_duplicate_members(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            fail(f"duplicate JSON object member: {key}")
        result[key] = value
    return result


def _reject_non_finite(value: str) -> NoReturn:
    fail(f"non-finite JSON number: {value}")


def load_json(root: Path, relative_path: Path) -> tuple[dict[str, Any], bytes]:
    path = root / relative_path
    try:
        raw = path.read_bytes()
    except OSError as exc:
        fail(f"cannot read {relative_path}: {exc.strerror or type(exc).__name__}")
    try:
        value = json.loads(
            raw,
            object_pairs_hook=_object_without_duplicate_members,
            parse_constant=_reject_non_finite,
        )
    except GuardError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(f"invalid JSON in {relative_path}: {type(exc).__name__}")
    return require_dict(value, str(relative_path)), raw


def validate_capabilities(root: Path) -> None:
    source, source_raw = load_json(root, CAPABILITY_LEDGER)
    reconciliation, _ = load_json(root, RECONCILIATION)
    rows = require_list(source.get("capabilities"), "source.capabilities")
    require(len(rows) == 45, "source capability total must be 45")

    counts = collections.Counter({disposition: 0 for disposition in DISPOSITIONS})
    keys: list[str] = []
    supported_count = 0
    derived_rows: list[dict[str, str]] = []
    non_supported_non_deferred: list[dict[str, str]] = []

    for index, raw_row in enumerate(rows):
        row = require_dict(raw_row, f"source.capabilities[{index}]")
        key = require_string(row.get("CapabilityKey"), f"source.capabilities[{index}].CapabilityKey")
        require("Disposition" in row, f"source capability {key} has no Disposition")
        disposition = row["Disposition"]
        require(type(disposition) is str and disposition in DISPOSITIONS, f"source capability {key} has invalid Disposition")
        claimed = row.get("CurrentlyClaimedSupported")
        require(type(claimed) is bool, f"source capability {key} has invalid CurrentlyClaimedSupported")
        require(claimed == (disposition in SUPPORTED_DISPOSITIONS), f"source capability {key} support claim conflicts with Disposition")
        parity = require_string(row.get("BehavioralParityStatus"), f"source capability {key}.BehavioralParityStatus")

        keys.append(key)
        counts[disposition] += 1
        supported_count += int(claimed)
        derived_rows.append({"CapabilityKey": key, "PrimaryDisposition": disposition})
        if not claimed and disposition != "DEFERRED_WITH_EXPLICIT_BOUNDED_JUSTIFICATION":
            non_supported_non_deferred.append(
                {
                    "CapabilityKey": key,
                    "Disposition": disposition,
                    "BehavioralParityStatus": parity,
                }
            )

    require(len(set(keys)) == len(keys), "duplicate CapabilityKey")
    require(sum(counts.values()) == 45, "primary disposition sum must be 45")
    require(counts["MISSING"] == 0, "MISSING disposition count must be zero")
    require(supported_count == 27, "supported capability count must be 27")
    require(counts["DEFERRED_WITH_EXPLICIT_BOUNDED_JUSTIFICATION"] == 14, "deferred capability count must be 14")
    require(len(non_supported_non_deferred) == 4, "non-supported non-deferred count must be four")

    actual_exact = {row["CapabilityKey"]: row["Disposition"] for row in non_supported_non_deferred}
    strict_equal(actual_exact, EXACT_NON_SUPPORTED, "four exact non-supported capabilities")
    for row in non_supported_non_deferred:
        require(row["BehavioralParityStatus"] == "NOT_APPLICABLE_BY_DISPOSITION", f"{row['CapabilityKey']} has invalid parity status")

    expected_reconciliation = {
        "schema_version": 1,
        "task": "ROADMAP_22_PHASE19_CAPABILITY_DISPOSITION_RECONCILIATION_V1",
        "source_ledger": str(CAPABILITY_LEDGER),
        "source_ledger_sha256": hashlib.sha256(source_raw).hexdigest(),
        "legacy_ffmpeg_capability_total": 45,
        "primary_disposition_order": DISPOSITIONS,
        "primary_disposition_counts": dict(counts),
        "primary_disposition_sum": 45,
        "capability_disposition_reconciliation": "PASS",
        "capability_disposition_reconciliation_delta": 0,
        "duplicate_primary_disposition_count": 0,
        "unaccounted_capability_count": 0,
        "unaccounted_capability_keys": [],
        "currently_claimed_supported_count": supported_count,
        "deferred_count": counts["DEFERRED_WITH_EXPLICIT_BOUNDED_JUSTIFICATION"],
        "non_supported_non_deferred_count": len(non_supported_non_deferred),
        "non_supported_non_deferred_capabilities": non_supported_non_deferred,
        "capabilities": derived_rows,
    }
    strict_equal(reconciliation, expected_reconciliation, "capability reconciliation")


def integer_count_map(value: Any, label: str) -> dict[str, int]:
    mapping = require_dict(value, label)
    for key, count in mapping.items():
        require_string(key, f"{label} key")
        require_int(count, f"{label}.{key}", 0)
    return mapping


def validate_test_accounting(root: Path) -> None:
    data, _ = load_json(root, TEST_ACCOUNTING)
    historical = require_dict(data.get("historical_report"), "test.historical_report")
    base = require_dict(data.get("fresh_base_reproduction"), "test.fresh_base_reproduction")
    candidate = require_dict(data.get("candidate_validation"), "test.candidate_validation")
    render_module = require_dict(require_dict(data.get("module_deltas"), "test.module_deltas").get("render-module"), "test.module_deltas.render-module")
    multiset = require_dict(data.get("render_case_multiset"), "test.render_case_multiset")

    strict_equal(historical.get("reported_tests"), 8138, "historical reported tests")
    strict_equal(historical.get("user_approximate_tests"), 8139, "user approximate tests")
    strict_equal(base.get("tests"), 8024, "fresh base tests")
    strict_equal(candidate.get("tests"), 7929, "candidate tests")
    require_int(base.get("passed"), "fresh base passed", 0)
    require_int(base.get("skipped"), "fresh base skipped", 0)
    require(base["passed"] + base["skipped"] == base["tests"], "fresh base result arithmetic mismatch")
    require_int(candidate.get("passed"), "candidate passed", 0)
    require_int(candidate.get("skipped"), "candidate skipped", 0)
    require(candidate["passed"] + candidate["skipped"] == candidate["tests"], "candidate result arithmetic mismatch")

    actual_delta = candidate["tests"] - base["tests"]
    require(actual_delta == -95, "fresh base to candidate delta must be -95")
    strict_equal(data.get("actual_test_surface_delta"), actual_delta, "actual test surface delta")
    strict_equal(data.get("non_render_base_tests"), 5098, "non-render base tests")
    strict_equal(data.get("non_render_candidate_tests"), 5098, "non-render candidate tests")
    strict_equal(data.get("non_render_delta"), data["non_render_candidate_tests"] - data["non_render_base_tests"], "non-render delta")
    strict_equal(data["non_render_delta"], 0, "non-render delta requirement")

    expected_render = {"base": 2926, "candidate": 2831, "delta": -95}
    strict_equal(render_module, expected_render, "render-module delta")
    require(data["non_render_base_tests"] + render_module["base"] == base["tests"], "base module partition mismatch")
    require(data["non_render_candidate_tests"] + render_module["candidate"] == candidate["tests"], "candidate module partition mismatch")

    removed_map = integer_count_map(data.get("removed_class_counts"), "test.removed_class_counts")
    added_map = integer_count_map(data.get("added_class_counts"), "test.added_class_counts")
    removed = sum(removed_map.values())
    added = sum(added_map.values())
    require((removed, added, added - removed) == (312, 217, -95), "removed/added test arithmetic mismatch")

    renamed = require_list(data.get("renamed_equivalent_test_classes"), "test.renamed_equivalent_test_classes")
    old_classes: set[str] = set()
    new_classes: set[str] = set()
    renamed_removed = 0
    renamed_added = 0
    for index, raw_row in enumerate(renamed):
        row = require_dict(raw_row, f"test.renamed[{index}]")
        old_class = require_string(row.get("old_class"), f"test.renamed[{index}].old_class")
        new_class = require_string(row.get("new_class"), f"test.renamed[{index}].new_class")
        require(old_class not in old_classes and new_class not in new_classes, "duplicate rename-equivalent class")
        old_classes.add(old_class)
        new_classes.add(new_class)
        old_cases = require_int(row.get("old_cases"), f"test.renamed[{index}].old_cases", 0)
        new_cases = require_int(row.get("new_cases"), f"test.renamed[{index}].new_cases", 0)
        require(removed_map.get(old_class) == old_cases, f"rename source count mismatch for {old_class}")
        require(added_map.get(new_class) == new_cases, f"rename target count mismatch for {new_class}")
        renamed_removed += old_cases
        renamed_added += new_cases
    require((renamed_removed, renamed_added, renamed_added - renamed_removed) == (144, 144, 0), "rename-equivalent arithmetic mismatch")

    groups = require_dict(data.get("deleted_test_class_groups"), "test.deleted_test_class_groups")
    deleted_classes: set[str] = set()
    deleted_total = 0
    for group_name, raw_group in groups.items():
        group = require_dict(raw_group, f"test.deleted_group.{group_name}")
        group_count = require_int(group.get("count"), f"test.deleted_group.{group_name}.count", 0)
        classes = require_list(group.get("classes"), f"test.deleted_group.{group_name}.classes")
        derived_group_count = 0
        for raw_class_name in classes:
            class_name = require_string(raw_class_name, f"test.deleted_group.{group_name}.class")
            require(class_name not in deleted_classes, f"duplicate deleted-group class: {class_name}")
            deleted_classes.add(class_name)
            require(class_name in removed_map, f"deleted-group class absent from removed counts: {class_name}")
            derived_group_count += removed_map[class_name]
        require(derived_group_count == group_count, f"deleted-group count mismatch: {group_name}")
        deleted_total += group_count
    require(deleted_total == 97, "whole-class deleted total must be 97")

    modified_removed = removed - renamed_removed - deleted_total
    modified_added = added - renamed_added
    modified_net = modified_added - modified_removed
    require((modified_removed, modified_added, modified_net) == (71, 73, 2), "modified same-class arithmetic mismatch")
    expected_multiset = {
        "base": 2926,
        "candidate": 2831,
        "delta": -95,
        "removed_cases": removed,
        "added_cases": added,
        "renamed_equivalent_removed": renamed_removed,
        "renamed_equivalent_added": renamed_added,
        "whole_class_deleted": deleted_total,
        "modified_same_class_removed": modified_removed,
        "modified_same_class_added": modified_added,
        "modified_same_class_net": modified_net,
    }
    strict_equal(multiset, expected_multiset, "render case multiset")
    require(base["tests"] - deleted_total + modified_net == candidate["tests"], "8024 - 97 + 2 must equal 7929")
    strict_equal(
        data.get("accounting_equation"),
        "8024 fresh base - 97 whole-class deletions + 2 modified-class net + 0 rename net = 7929 candidate",
        "test accounting equation",
    )
    strict_equal(data.get("unexplained_test_count_reduction"), 0, "unexplained test count reduction")
    strict_equal(data.get("unexplained_behavioral_test_loss"), 0, "unexplained behavioral test loss")
    strict_equal(data.get("genuinely_lost_behavioral_coverage_count"), 0, "genuinely lost behavioral coverage")


def validate_semgrep_accounting(root: Path) -> None:
    data, _ = load_json(root, SEMGREP_ACCOUNTING)
    strict_equal(data.get("base_target_count"), 3722, "Semgrep base target count")
    strict_equal(data.get("candidate_target_count"), 3697, "Semgrep candidate target count")
    derived_delta = data["candidate_target_count"] - data["base_target_count"]
    require(derived_delta == -25, "Semgrep target delta must be -25")
    strict_equal(data.get("target_delta"), derived_delta, "Semgrep target delta")
    strict_equal(data.get("removed_target_count"), 92, "Semgrep removed target count")
    strict_equal(data.get("added_target_count"), 67, "Semgrep added target count")
    derived_net = data["added_target_count"] - data["removed_target_count"]
    require(derived_net == -25, "Semgrep added-minus-removed net must be -25")
    strict_equal(data.get("removed_plus_added_net"), derived_net, "Semgrep removed-plus-added net")

    for field in ("base_findings", "base_errors", "candidate_findings", "candidate_errors"):
        strict_equal(data.get(field), 0, f"Semgrep {field}")
    strict_equal(data.get("semgrep_target_delta_explained"), "YES", "Semgrep explained label")

    changes = require_list(data.get("target_changes"), "semgrep.target_changes")
    allowed = {
        "ADDED_GUARD_TEST_OR_GOVERNANCE_TARGET",
        "DELETED_CONCRETE_OR_RETIRED_TEST_SURFACE",
        "RENAMED_PROVIDER_NEUTRAL_SOURCE",
        "RENAMED_PROVIDER_NEUTRAL_TARGET",
    }
    derived_counts: collections.Counter[str] = collections.Counter()
    paths: set[str] = set()
    rename_sources: list[dict[str, Any]] = []
    rename_targets: set[str] = set()
    for index, raw_change in enumerate(changes):
        change = require_dict(raw_change, f"semgrep.target_changes[{index}]")
        path = require_string(change.get("path"), f"semgrep.target_changes[{index}].path")
        require(path not in paths, f"duplicate Semgrep target-change path: {path}")
        paths.add(path)
        classification = change.get("classification")
        require(type(classification) is str and classification in allowed, f"invalid Semgrep classification for {path}")
        derived_counts[classification] += 1
        if classification == "RENAMED_PROVIDER_NEUTRAL_SOURCE":
            rename_sources.append(change)
        elif classification == "RENAMED_PROVIDER_NEUTRAL_TARGET":
            rename_targets.add(path)

    classification_counts = integer_count_map(data.get("classification_counts"), "semgrep.classification_counts")
    strict_equal(classification_counts, dict(derived_counts), "Semgrep classification counts")
    removed = derived_counts["DELETED_CONCRETE_OR_RETIRED_TEST_SURFACE"] + derived_counts["RENAMED_PROVIDER_NEUTRAL_SOURCE"]
    added = derived_counts["ADDED_GUARD_TEST_OR_GOVERNANCE_TARGET"] + derived_counts["RENAMED_PROVIDER_NEUTRAL_TARGET"]
    require((removed, added) == (data["removed_target_count"], data["added_target_count"]), "Semgrep classification partition mismatch")
    require(sum(derived_counts.values()) == removed + added, "Semgrep classifications do not sum to removed plus added")
    require(len(rename_sources) == len(rename_targets), "Semgrep rename source/target count mismatch")
    source_targets = {
        require_string(change.get("rename_target"), "Semgrep rename source target") for change in rename_sources
    }
    require(source_targets == rename_targets, "Semgrep rename source/target mapping mismatch")


def git_output(root: Path, arguments: list[str]) -> bytes:
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        fail(f"cannot execute Git: {exc.strerror or type(exc).__name__}")
    if completed.returncode != 0:
        fail(f"Git command failed: git {' '.join(arguments)}")
    return completed.stdout


def decode_git_paths(raw: bytes) -> set[str]:
    try:
        parts = raw.decode("utf-8").split("\0")
    except UnicodeDecodeError:
        fail("Git returned a non-UTF-8 path")
    paths = [path for path in parts if path]
    require(len(paths) == len(set(paths)), "Git returned duplicate changed paths")
    return set(paths)


def git_returncode(root: Path, arguments: list[str]) -> int:
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        fail(f"cannot execute Git: {exc.strerror or type(exc).__name__}")
    return completed.returncode


def require_ancestor(root: Path, ancestor: str, descendant: str, failure_message: str) -> None:
    result = git_returncode(root, ["merge-base", "--is-ancestor", ancestor, descendant])
    if result == 1:
        fail(failure_message)
    require(result == 0, "Git could not determine required commit ancestry")


def require_object_id(value: Any, label: str) -> str:
    object_id = require_string(value, label)
    require(
        len(object_id) == 40 and all(character in "0123456789abcdef" for character in object_id),
        f"{label} must be a 40-character lowercase hexadecimal object ID",
    )
    return object_id


def decoded_object_id(raw: bytes, label: str) -> str:
    try:
        value = raw.decode("ascii").strip()
    except UnicodeDecodeError:
        fail(f"Git returned a non-ASCII {label}")
    return require_object_id(value, label)


def validated_canonical_anchor(root: Path, anchor_sha: str, anchor_tree: str) -> str:
    anchor_sha = require_object_id(anchor_sha, "canonical lineage anchor SHA")
    anchor_tree = require_object_id(anchor_tree, "canonical lineage anchor tree")
    if git_returncode(root, ["cat-file", "-e", f"{anchor_sha}^{{commit}}"]):
        fail("canonical lineage anchor commit is unknown or is not a commit")
    actual_tree = decoded_object_id(
        git_output(root, ["rev-parse", "--verify", f"{anchor_sha}^{{tree}}"]),
        "canonical lineage anchor commit tree",
    )
    require(actual_tree == anchor_tree, "canonical lineage anchor tree mismatch")
    return anchor_sha


def require_anchor_at_current_head_or_ancestor(root: Path, anchor_sha: str) -> None:
    current_head = decoded_object_id(
        git_output(root, ["rev-parse", "--verify", "HEAD^{commit}"]),
        "current HEAD commit",
    )
    require_ancestor(
        root,
        anchor_sha,
        current_head,
        "canonical lineage anchor is not an ancestor of current HEAD",
    )


def historical_candidate_scope(
    root: Path,
    base_sha: str,
    reviewed_sha: str,
    reviewed_tree: str,
    anchor_sha: str,
    anchor_tree: str,
) -> set[str]:
    require(
        not git_output(root, ["status", "--porcelain=v1", "-z", "--untracked-files=all"]),
        "historical evidence validation requires a clean repository",
    )
    base_sha = require_object_id(base_sha, "clean-forward base_sha")
    reviewed_sha = require_object_id(reviewed_sha, "historical evidence SHA")
    reviewed_tree = require_object_id(reviewed_tree, "historical evidence tree")

    if git_returncode(root, ["cat-file", "-e", f"{reviewed_sha}^{{commit}}"]):
        fail("reviewed evidence commit is unknown or is not a commit")
    actual_tree = decoded_object_id(
        git_output(root, ["rev-parse", "--verify", f"{reviewed_sha}^{{tree}}"]),
        "reviewed evidence commit tree",
    )
    require(actual_tree == reviewed_tree, "reviewed evidence tree mismatch")
    anchor_sha = validated_canonical_anchor(root, anchor_sha, anchor_tree)

    require_ancestor(
        root,
        base_sha,
        reviewed_sha,
        "clean-forward base is not an ancestor of reviewed evidence commit",
    )
    require_ancestor(
        root,
        reviewed_sha,
        anchor_sha,
        "reviewed evidence commit is not an ancestor of canonical lineage anchor",
    )
    require_anchor_at_current_head_or_ancestor(root, anchor_sha)

    return decode_git_paths(git_output(root, ["diff", "--name-only", "-z", base_sha, reviewed_sha, "--"]))


def candidate_scope(
    root: Path,
    base_sha: str,
    reviewed_sha: str,
    reviewed_tree: str,
    anchor_sha: str,
    anchor_tree: str,
) -> set[str]:
    staged = decode_git_paths(git_output(root, ["diff", "--cached", "--name-only", "-z", "--"]))
    if staged:
        anchor_sha = validated_canonical_anchor(root, anchor_sha, anchor_tree)
        require_anchor_at_current_head_or_ancestor(root, anchor_sha)
        return staged
    return historical_candidate_scope(root, base_sha, reviewed_sha, reviewed_tree, anchor_sha, anchor_tree)


def validate_clean_forward(
    root: Path,
    reviewed_sha: str,
    reviewed_tree: str,
    anchor_sha: str,
    anchor_tree: str,
) -> None:
    data, _ = load_json(root, CLEAN_FORWARD_LEDGER)
    strict_equal(data.get("raw_finding_tuple_count"), 5177, "clean-forward raw tuple count")
    strict_equal(data.get("initial_finding_path_count"), 311, "clean-forward initial path count")
    paths_data = require_list(data.get("paths"), "clean-forward.paths")
    base_sha = require_string(data.get("base_sha"), "clean-forward base_sha")
    candidate_paths = candidate_scope(root, base_sha, reviewed_sha, reviewed_tree, anchor_sha, anchor_tree)

    ledger_paths: set[str] = set()
    initial_paths: set[str] = set()
    tuple_sum = 0
    for index, raw_entry in enumerate(paths_data):
        entry = require_dict(raw_entry, f"clean-forward.paths[{index}]")
        path = require_string(entry.get("path"), f"clean-forward.paths[{index}].path")
        require(path not in ledger_paths, f"duplicate CLEAN FORWARD path: {path}")
        ledger_paths.add(path)
        disposition = entry.get("disposition")
        require(type(disposition) is str and bool(disposition.strip()), f"unclassified CLEAN FORWARD path: {path}")
        require(disposition.upper() not in {"UNCLASSIFIED", "KEEP"}, f"unexplained CLEAN FORWARD disposition for {path}")
        require(disposition in CLEAN_FORWARD_DISPOSITIONS, f"unknown CLEAN FORWARD disposition for {path}")
        initial = entry.get("initial_finding_path")
        require(type(initial) is bool, f"invalid initial_finding_path for {path}")
        finding_count = require_int(entry.get("finding_tuple_count"), f"finding_tuple_count for {path}", 0)
        if initial:
            initial_paths.add(path)
            tuple_sum += finding_count

    require(len(ledger_paths) == len(paths_data), "CLEAN FORWARD path uniqueness mismatch")
    require(len(initial_paths) == 311, "derived initial finding path count must be 311")
    require(tuple_sum == 5177, "derived raw finding tuple count must be 5177")
    strict_equal(data.get("path_count"), len(ledger_paths), "clean-forward path count")
    strict_equal(data.get("candidate_scope_path_count"), len(candidate_paths), "clean-forward candidate scope path count")

    uncovered = candidate_paths - ledger_paths
    supplemental = (ledger_paths & candidate_paths) - initial_paths
    stale = ledger_paths - candidate_paths - initial_paths
    duplicates = len(paths_data) - len(ledger_paths)
    unclassified = sum(
        1
        for entry in paths_data
        if type(entry.get("disposition")) is not str
        or not entry.get("disposition", "").strip()
        or entry.get("disposition", "").upper() == "UNCLASSIFIED"
    )
    unexplained_keep = sum(
        1 for entry in paths_data if type(entry.get("disposition")) is str and entry["disposition"].upper() == "KEEP"
    )
    strict_equal(data.get("supplemental_candidate_scope_path_count"), len(supplemental), "clean-forward supplemental path count")
    strict_equal(data.get("duplicate_path_count"), duplicates, "clean-forward duplicate path count")
    strict_equal(data.get("unclassified_count"), unclassified, "clean-forward unclassified count")
    strict_equal(data.get("unexplained_keep_count"), unexplained_keep, "clean-forward unexplained KEEP count")
    strict_equal(data.get("candidate_scope_uncovered_count"), len(uncovered), "clean-forward uncovered count")
    strict_equal(data.get("stale_finding_path_count"), len(stale), "clean-forward stale path count")
    require(not uncovered, "candidate scope contains uncovered paths")
    require(not stale, "CLEAN FORWARD ledger contains stale unexplained paths")

    for entry in paths_data:
        path = entry["path"]
        expected_supplemental = path in supplemental
        if expected_supplemental:
            require(entry.get("supplemental_candidate_scope") is True, f"missing supplemental marker for {path}")
        else:
            require("supplemental_candidate_scope" not in entry, f"unexpected supplemental marker for {path}")


def validate(
    root: Path,
    reviewed_sha: str,
    reviewed_tree: str,
    anchor_sha: str,
    anchor_tree: str,
) -> None:
    root = root.resolve(strict=True)
    validate_capabilities(root)
    validate_test_accounting(root)
    validate_semgrep_accounting(root)
    validate_clean_forward(root, reviewed_sha, reviewed_tree, anchor_sha, anchor_tree)


def parse_args() -> argparse.Namespace:
    default_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root, help="repository root (default: script parent)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        validate(
            args.root,
            HISTORICAL_EVIDENCE_SHA,
            HISTORICAL_EVIDENCE_TREE,
            CANONICAL_LINEAGE_ANCHOR_SHA,
            CANONICAL_LINEAGE_ANCHOR_TREE,
        )
    except (GuardError, OSError, RuntimeError) as exc:
        print(f"FINAL_GOVERNANCE_EVIDENCE_GATE=FAIL: {exc}", file=sys.stderr)
        return 1
    print(PASS_OUTPUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
