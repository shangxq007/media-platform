#!/usr/bin/env python3
"""Fail-closed guard for the Phase 17 sandbox ledger."""
from __future__ import annotations
import argparse, csv, re, sys
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
    "UNCLASSIFIED_COUNT", "DUPLICATE_ROW_COUNT",
}
EXPECTED_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_DECISION_RECOVERY_FINAL_REVIEW"

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
        if not Path(p).is_file(): fail(f"row {i} exact path does not exist: {p}")
        if row["disposition"] not in ALLOWED: fail(f"row {i} invalid disposition")
    ids = [r["row_id"] for r in rows]
    paths = [r["exact_path"] for r in rows]
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
    }
    if declared != actual: fail(f"declared metrics {declared} != actual {actual}")
    if sum(counts[x] for x in ALLOWED) != len(rows): fail("disposition sum mismatch")
    if unclassified != 0 or duplicate_count != 0: fail("ledger not closed")
    state = yaml.safe_load(state_path.read_text())
    immediate = state["governance_execution"]["immediate_next_gate"]
    next_gate = state["governance"]["next_gate"]
    if immediate != EXPECTED_GATE or next_gate != EXPECTED_GATE or immediate != next_gate:
        fail("governance next-gate surfaces inconsistent")
    if state["roadmap_22"]["phase_17_started"] is not False: fail("Phase 17 implementation started")
    if state["roadmap_23"]["status"] != "NOT_STARTED": fail("Roadmap 23 started")
    community = state["governance_execution"]["adopted_cross_cutting_amendments"]["community_compute_distributed_runtime_foundation"]
    if community != "ADOPTED_DEFERRED": fail("Community Compute state changed")
    dr = state["roadmap_22"].get("phase_17_sandbox_isolation_decision_recovery")
    if dr != "FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW": fail("Decision Recovery state not frozen pending review")
    print(f"PHASE17_SANDBOX_LEDGER_GUARD=PASS rows={len(rows)} dispositions={dict(sorted(counts.items()))}")

if __name__ == "__main__":
    main()
