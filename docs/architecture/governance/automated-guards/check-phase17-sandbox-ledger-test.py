#!/usr/bin/env python3
"""Executable RED matrix for the fail-closed Phase 17 ledger guard."""
import copy
import csv
import subprocess
import tempfile
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[4]
GUARD = ROOT / "docs/architecture/governance/automated-guards/check-phase17-sandbox-ledger.py"
DOC = ROOT / "docs/architecture/governance/roadmap-22-phase-17-sandbox-isolation-decision-recovery.md"
LEDGER = ROOT / "docs/architecture/governance/automated-guards/phase17-sandbox-isolation-clean-forward-ledger.tsv"
STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"
DECISION_RECOVERY_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_DECISION_RECOVERY_FINAL_REVIEW"
CORRECTION_18_FCV_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_BOUNDED_IMPLEMENTATION_CORRECTION_18_FCV_REVIEW"

def invoke(doc, ledger, state):
    return subprocess.run(["python3", str(GUARD), "--document", str(doc),
        "--ledger", str(ledger), "--state", str(state)], cwd=ROOT, capture_output=True)

def write_rows(path, fields, rows):
    with path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter="\t", lineterminator="\n",
                                extrasaction="ignore")
        writer.writeheader(); writer.writerows(rows)

def write_state(path, state):
    path.write_text(yaml.safe_dump(state, sort_keys=False))

def main():
    with LEDGER.open() as f:
        reader = csv.DictReader(f, delimiter="\t"); fields = reader.fieldnames; rows = list(reader)
    baseline_state = yaml.safe_load(STATE.read_text())
    with tempfile.TemporaryDirectory(prefix="phase17-ledger-red-") as directory:
        root = Path(directory); doc = root / "doc.md"; ledger = root / "ledger.tsv"; state = root / "state.yaml"
        doc.write_text(DOC.read_text()); write_state(state, baseline_state); write_rows(ledger, fields, rows)
        if invoke(doc, ledger, state).returncode != 0: raise SystemExit("baseline did not pass")
        mutations = []
        mutations.append(("missing-column", fields[:-1], rows))
        empty = [dict(row) for row in rows]; empty[0]["rationale"] = ""
        mutations.append(("empty-field", fields, empty))
        aggregated = [dict(row) for row in rows]; aggregated[0]["exact_path"] = "concepts/tests"
        mutations.append(("aggregated-path", fields, aggregated))
        invalid = [dict(row) for row in rows]; invalid[0]["disposition"] = "UNKNOWN"
        mutations.append(("invalid-disposition", fields, invalid))
        for name, mutation_fields, mutation_rows in mutations:
            write_rows(ledger, mutation_fields, mutation_rows)
            if invoke(doc, ledger, state).returncode == 0: raise SystemExit(f"mutation passed: {name}")
        write_rows(ledger, fields, rows); doc.write_text(DOC.read_text().replace("TOTAL_ROWS=131", "TOTAL_ROWS=130"))
        if invoke(doc, ledger, state).returncode == 0: raise SystemExit("mutation passed: count-mismatch")
        doc.write_text(DOC.read_text())

        state_mutations = []
        changed = copy.deepcopy(baseline_state)
        changed["governance_execution"]["immediate_next_gate"] = "INVALID_GATE"
        state_mutations.append(("gate-mismatch", changed))
        changed = copy.deepcopy(baseline_state)
        changed["governance_execution"]["immediate_next_gate"] = "ARBITRARY_AGREED_GATE"
        changed["governance"]["next_gate"] = "ARBITRARY_AGREED_GATE"
        state_mutations.append(("aligned-arbitrary-gates", changed))
        changed = copy.deepcopy(baseline_state)
        changed["repository"]["canonical_main"]["sha"] = "0" * 40
        state_mutations.append(("canonical-main-sha-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["repository"]["canonical_main"]["tree"] = "0" * 40
        state_mutations.append(("canonical-main-tree-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["repository"].pop("canonical_main")
        state_mutations.append(("canonical-main-missing", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_22"]["phase_17_started"] = False
        state_mutations.append(("implementation-phase17-started-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_22"]["phase_17_sandbox_isolation_decision_recovery"] = (
            "FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW")
        state_mutations.append(("mixed-decision-recovery-state", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_22"]["phase_17_sandbox_isolation_bounded_implementation"] = "IN_PROGRESS"
        state_mutations.append(("implementation-state-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_22"]["phase_18_started"] = True
        state_mutations.append(("phase18-started-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_22"]["phase_19_started"] = True
        state_mutations.append(("phase19-started-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["roadmap_23"]["status"] = "IN_PROGRESS"
        state_mutations.append(("roadmap23-state-drift", changed))
        changed = copy.deepcopy(baseline_state)
        changed["governance_execution"]["adopted_cross_cutting_amendments"][
            "community_compute_distributed_runtime_foundation"] = "ADOPTED"
        state_mutations.append(("community-compute-state-drift", changed))

        changed = copy.deepcopy(baseline_state)
        changed["repository"]["active_governed_branch"]["name"] = (
            "agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery")
        state_mutations.append(("active-governed-branch-drift", changed))

        decision_state = copy.deepcopy(baseline_state)
        decision_state["repository"]["canonical_main"] = {
            "sha": "d2cc856939fe0a73d6f1ef799078a0a5e7c5b179",
            "tree": "d2e68f5af848cb49a5db1ea33cd8629ad5b250e0",
        }
        decision_state["repository"]["active_governed_branch"]["name"] = (
            "agent/roadmap22-phase17-sandbox-isolation-decision-recovery")
        roadmap_22 = decision_state["roadmap_22"]
        roadmap_22["phase_17_started"] = False
        roadmap_22["phase_17_sandbox_isolation_decision_recovery"] = (
            "FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW")
        roadmap_22.pop("phase_17_sandbox_isolation_bounded_implementation")
        roadmap_22.pop("phase_18_started")
        roadmap_22.pop("phase_19_started")
        decision_state["governance_execution"]["immediate_next_gate"] = DECISION_RECOVERY_GATE
        decision_state["governance_execution"]["next_roadmap_execution_after_governance_gate"]["started"] = False
        decision_state["governance"]["next_gate"] = DECISION_RECOVERY_GATE
        write_state(state, decision_state)
        if invoke(doc, ledger, state).returncode != 0:
            raise SystemExit("decision recovery candidate baseline did not pass")
        changed = copy.deepcopy(decision_state)
        changed["roadmap_22"]["phase_17_started"] = True
        state_mutations.append(("decision-recovery-phase17-started-drift", changed))
        changed = copy.deepcopy(decision_state)
        changed["governance_execution"]["immediate_next_gate"] = CORRECTION_18_FCV_GATE
        changed["governance"]["next_gate"] = CORRECTION_18_FCV_GATE
        state_mutations.append(("decision-recovery-gate-drift", changed))

        for name, mutation_state in state_mutations:
            write_state(state, mutation_state)
            if invoke(doc, ledger, state).returncode == 0:
                raise SystemExit(f"mutation passed: {name}")
    print(f"PHASE17_SANDBOX_LEDGER_RED_MATRIX=PASS mutations={len(mutations) + 1 + len(state_mutations)}")

if __name__ == "__main__":
    main()
