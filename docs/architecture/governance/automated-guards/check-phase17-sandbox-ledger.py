#!/usr/bin/env python3
"""Fail-closed guard for the Phase 17 sandbox ledger."""
from __future__ import annotations
import argparse, csv, re, subprocess, sys
from collections import Counter
from pathlib import Path
import yaml

REQUIRED = [
    "row_id", "exact_path", "symbol_or_component", "current_callers",
    "current_dependency_direction", "current_runtime_role",
    "current_authority_claim", "phase_17_relevance", "disposition", "rationale",
]
ALLOWED = {
    "REUSE_AS_CANONICAL", "REUSE_MECHANICS_ONLY", "MIGRATE_REDESIGN",
    "DELETE_SHADOW", "DEFER",
}
METRICS = {
    "TOTAL_ROWS", "REUSE_AS_CANONICAL_COUNT", "REUSE_MECHANICS_ONLY_COUNT",
    "MIGRATE_REDESIGN_COUNT", "DELETE_SHADOW_COUNT", "DEFER_COUNT",
    "UNCLASSIFIED_COUNT", "DUPLICATE_ROW_COUNT", "PLACEHOLDER_PATH_COUNT",
    "GLOB_PATH_COUNT",
}
DECISION_RECOVERY_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_DECISION_RECOVERY_FINAL_REVIEW"
CORRECTION_17_FCV_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_BOUNDED_IMPLEMENTATION_CORRECTION_17_FCV_REVIEW"
EXPECTED_CANONICAL_MAIN = {
    "sha": "d2cc856939fe0a73d6f1ef799078a0a5e7c5b179",
    "tree": "d2e68f5af848cb49a5db1ea33cd8629ad5b250e0",
}
EXPECTED_ROW_IDS = [f"P17-L-{number:03d}" for number in range(1, 132)]
GIT_QUALIFIED_PATH = re.compile(r"^(?P<revision>[0-9a-f]{40}):(?P<path>.+)$")
ABSENT = object()

DECISION_RECOVERY_CANDIDATE = (
    False,
    "FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW",
    ABSENT,
    ABSENT,
    ABSENT,
    DECISION_RECOVERY_GATE,
    DECISION_RECOVERY_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
)
CORRECTION_15_FROZEN_CANDIDATE = (
    True,
    "CLOSED",
    "FROZEN_CANDIDATE_PENDING_FCV",
    False,
    False,
    CORRECTION_17_FCV_GATE,
    CORRECTION_17_FCV_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
)
ACCEPTED_PHASE_STATES = {
    DECISION_RECOVERY_CANDIDATE,
    CORRECTION_15_FROZEN_CANDIDATE,
}

def fail(msg: str) -> None:
    print(f"FAIL: {msg}", file=sys.stderr)
    raise SystemExit(1)

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--document", default="docs/architecture/governance/roadmap-22-phase-17-sandbox-isolation-decision-recovery.md")
    ap.add_argument("--ledger", default="docs/architecture/governance/automated-guards/phase17-sandbox-isolation-clean-forward-ledger.tsv")
    ap.add_argument("--state", default="docs/architecture/governance/project-state/current-state.yaml")
    args = ap.parse_args()
    doc_path, ledger_path, state_path = map(Path, (args.document, args.ledger, args.state))
    for p in (doc_path, ledger_path, state_path):
        if not p.is_file(): fail(f"missing required file: {p}")
    doc = doc_path.read_text()
    with ledger_path.open(newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        if reader.fieldnames != REQUIRED: fail(f"ledger columns differ: {reader.fieldnames}")
        rows = list(reader)
    if not rows: fail("ledger parsed zero rows")
    for i, row in enumerate(rows, 1):
        for col in REQUIRED:
            if not row.get(col, "").strip(): fail(f"row {i} empty mandatory field {col}")
        p = row["exact_path"]
        if "..." in p: fail(f"row {i} ellipsis path")
        if "and tests" in p.lower() or "concepts/tests" in p.lower(): fail(f"row {i} aggregated path")
        if "*" in p or "?" in p or "[" in p or "]" in p: fail(f"row {i} undeclared glob path")
        if row["disposition"] not in ALLOWED: fail(f"row {i} invalid disposition")
        historical = GIT_QUALIFIED_PATH.fullmatch(p)
        if historical:
            if row["disposition"] != "DELETE_SHADOW":
                fail(f"row {i} Git-qualified path is only valid for DELETE_SHADOW: {p}")
            source_path = historical.group("path")
            if Path(source_path).exists():
                fail(f"row {i} DELETE_SHADOW source path still exists: {source_path}")
            evidence = subprocess.run(
                ["git", "cat-file", "-e", p], capture_output=True, check=False)
            if evidence.returncode != 0:
                fail(f"row {i} Git-qualified deletion evidence does not exist: {p}")
        else:
            if row["disposition"] == "DELETE_SHADOW":
                fail(f"row {i} DELETE_SHADOW lacks Git-qualified deletion evidence: {p}")
            if not Path(p).is_file():
                fail(f"row {i} exact path does not exist: {p}")
    ids = [r["row_id"] for r in rows]
    paths = [r["exact_path"] for r in rows]
    if ids != EXPECTED_ROW_IDS:
        fail("ledger ROW_ID denominator differs from frozen P17-L-001..P17-L-131 set")
    duplicate_count = len(ids)-len(set(ids)) + len(paths)-len(set(paths))
    counts = Counter(r["disposition"] for r in rows)
    unclassified = sum(1 for r in rows if r["disposition"] not in ALLOWED)
    found = {k: re.findall(rf"(?m)^{re.escape(k)}=(\d+)$", doc) for k in METRICS}
    for key, values in found.items():
        if len(values) != 1: fail(f"metric {key} appears {len(values)} times")
    declared = {k: int(v[0]) for k, v in found.items()}
    actual = {
        "TOTAL_ROWS": len(rows),
        "REUSE_AS_CANONICAL_COUNT": counts["REUSE_AS_CANONICAL"],
        "REUSE_MECHANICS_ONLY_COUNT": counts["REUSE_MECHANICS_ONLY"],
        "MIGRATE_REDESIGN_COUNT": counts["MIGRATE_REDESIGN"],
        "DELETE_SHADOW_COUNT": counts["DELETE_SHADOW"],
        "DEFER_COUNT": counts["DEFER"],
        "UNCLASSIFIED_COUNT": unclassified,
        "DUPLICATE_ROW_COUNT": duplicate_count,
        "PLACEHOLDER_PATH_COUNT": 0,
        "GLOB_PATH_COUNT": 0,
    }
    if declared != actual: fail(f"declared metrics {declared} != actual {actual}")
    if sum(counts[x] for x in ALLOWED) != len(rows): fail("disposition sum mismatch")
    if unclassified != 0 or duplicate_count != 0: fail("ledger not closed")
    state = yaml.safe_load(state_path.read_text())
    if not isinstance(state, dict):
        fail("governance state is not a mapping")
    canonical_main = state.get("repository", {}).get("canonical_main", ABSENT)
    if canonical_main != EXPECTED_CANONICAL_MAIN:
        fail(
            "persisted repository.canonical_main differs: "
            f"expected={EXPECTED_CANONICAL_MAIN} actual={canonical_main}")
    roadmap_22 = state["roadmap_22"]
    governance_execution = state["governance_execution"]
    phase_state = (
        roadmap_22.get("phase_17_started", ABSENT),
        roadmap_22.get("phase_17_sandbox_isolation_decision_recovery", ABSENT),
        roadmap_22.get("phase_17_sandbox_isolation_bounded_implementation", ABSENT),
        roadmap_22.get("phase_18_started", ABSENT),
        roadmap_22.get("phase_19_started", ABSENT),
        governance_execution.get("immediate_next_gate", ABSENT),
        state["governance"].get("next_gate", ABSENT),
        state["roadmap_23"].get("status", ABSENT),
        governance_execution["adopted_cross_cutting_amendments"].get(
            "community_compute_distributed_runtime_foundation", ABSENT),
    )
    if phase_state not in ACCEPTED_PHASE_STATES:
        fail("governance Phase 17 state does not match an accepted transition")
    print(
        f"PHASE17_SANDBOX_LEDGER_GUARD=PASS rows={len(rows)} "
        f"dispositions={dict(sorted(counts.items()))} unclassified={unclassified} "
        f"duplicates={duplicate_count} placeholders=0 globs=0")

if __name__ == "__main__":
    main()
