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
TRACKS = ROOT / "docs/architecture/governance/project-state/roadmap-tracks.yaml"
CLOSURE = ROOT / "docs/architecture/governance/roadmap-22-phase-18-faof-2-closure-publication.md"

ACCEPTED_SHA = "f00c0f36f7686314f6bb75a6b414751f66b95f9a"
ACCEPTED_TREE = "4b2ccb4c1161d1c4517a1d71b17616e6d8198595"
CLOSURE_GATE = "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_AUTHORIZED_PENDING"
CLOSURE_ACTION = (
    "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
    "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_AUTHORIZED_PENDING)"
)
PHASE19_AUTHORIZATION = (
    "AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION"
)


def invoke(doc, ledger, state, tracks, closure):
    return subprocess.run(
        [
            "python3",
            str(GUARD),
            "--document",
            str(doc),
            "--ledger",
            str(ledger),
            "--state",
            str(state),
            "--tracks",
            str(tracks),
            "--closure-publication",
            str(closure),
        ],
        cwd=ROOT,
        capture_output=True,
    )


def write_rows(path, fields, rows):
    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=fields,
            delimiter="\t",
            lineterminator="\n",
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(rows)


def write_state(path, state):
    path.write_text(yaml.safe_dump(state, sort_keys=False))


def expect_red(name, result):
    if result.returncode == 0:
        raise SystemExit(f"mutation passed: {name}")


def main():
    with LEDGER.open() as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        fields = reader.fieldnames
        rows = list(reader)

    baseline_state = yaml.safe_load(STATE.read_text())
    accepted = baseline_state.get("repository", {}).get("accepted_implementation", {})
    roadmap_22 = baseline_state.get("roadmap_22", {})
    governance_execution = baseline_state.get("governance_execution", {})
    if (
        accepted.get("milestone") != "ROADMAP_22_PHASE_18_FAOF_2"
        or accepted.get("sha") != ACCEPTED_SHA
        or accepted.get("tree") != ACCEPTED_TREE
        or accepted.get("accepted_implementation_remote_reachable") is not True
        or roadmap_22.get("phase_18") != "CLOSED"
        or roadmap_22.get("phase_18_faof_2_decision_recovery") != "PASS"
        or roadmap_22.get("phase_18_faof_2_bounded_implementation") != "CLOSED"
        or roadmap_22.get("phase_18_faof_2_bounded_implementation_acceptance")
        != "ACCEPTED"
        or roadmap_22.get("phase_19_started") is not False
        or roadmap_22.get("phase_19_implementation_authorization")
        != PHASE19_AUTHORIZATION
        or governance_execution.get("immediate_next_gate") != CLOSURE_GATE
        or baseline_state.get("governance", {}).get("next_gate") != CLOSURE_GATE
        or baseline_state.get("roadmap_23", {}).get("status") != "NOT_STARTED"
    ):
        raise SystemExit("baseline does not carry the exact Phase18 closure-publication state")

    with tempfile.TemporaryDirectory(prefix="phase17-ledger-red-") as directory:
        root = Path(directory)
        doc = root / "doc.md"
        ledger = root / "ledger.tsv"
        state = root / "state.yaml"
        tracks = root / "tracks.yaml"
        closure = root / "closure.md"
        doc.write_text(DOC.read_text())
        write_rows(ledger, fields, rows)
        write_state(state, baseline_state)
        tracks.write_text(TRACKS.read_text())
        closure.write_text(CLOSURE.read_text())

        baseline = invoke(doc, ledger, state, tracks, closure)
        if baseline.returncode != 0:
            raise SystemExit(
                "exact Phase18 closure-publication baseline did not pass: "
                + baseline.stderr.decode()
            )

        red_count = 0
        ledger_mutations = []
        ledger_mutations.append(("missing-column", fields[:-1], rows))
        empty = [dict(row) for row in rows]
        empty[0]["rationale"] = ""
        ledger_mutations.append(("empty-field", fields, empty))
        aggregated = [dict(row) for row in rows]
        aggregated[0]["exact_path"] = "concepts/tests"
        ledger_mutations.append(("aggregated-path", fields, aggregated))
        invalid = [dict(row) for row in rows]
        invalid[0]["disposition"] = "UNKNOWN"
        ledger_mutations.append(("invalid-disposition", fields, invalid))
        for name, mutation_fields, mutation_rows in ledger_mutations:
            write_rows(ledger, mutation_fields, mutation_rows)
            expect_red(name, invoke(doc, ledger, state, tracks, closure))
            red_count += 1
        write_rows(ledger, fields, rows)

        doc.write_text(DOC.read_text().replace("TOTAL_ROWS=131", "TOTAL_ROWS=130"))
        expect_red("count-mismatch", invoke(doc, ledger, state, tracks, closure))
        red_count += 1
        doc.write_text(DOC.read_text())

        closure_text = CLOSURE.read_text()
        for name, old, new in (
            ("closure-accepted-sha", ACCEPTED_SHA, "0" * 40),
            ("closure-accepted-tree", ACCEPTED_TREE, "0" * 40),
            ("closure-final-review", "CHATGPT_FINAL_REVIEW=PASS", "CHATGPT_FINAL_REVIEW=FAIL"),
            ("closure-ci", "STANDARD_CI_RUN=33064958899", "STANDARD_CI_RUN=0"),
            ("closure-foundation", "FOUNDATION_VERIFICATION_RUN=33064958805", "FOUNDATION_VERIFICATION_RUN=0"),
            ("closure-phase19", f"PHASE_19_IMPLEMENTATION={PHASE19_AUTHORIZATION}", "PHASE_19_IMPLEMENTATION=STARTED"),
        ):
            closure.write_text(closure_text.replace(old, new))
            expect_red(name, invoke(doc, ledger, state, tracks, closure))
            red_count += 1
        closure.write_text(closure_text)

        state_mutations = []

        def mutate(name, mutation):
            changed = copy.deepcopy(baseline_state)
            mutation(changed)
            state_mutations.append((name, changed))

        mutate(
            "old-in-progress-state",
            lambda data: (
                data["roadmap_22"].__setitem__("phase_18", "IN_PROGRESS"),
                data["roadmap_22"].__setitem__(
                    "phase_18_faof_2_bounded_implementation", "IN_PROGRESS"
                ),
            ),
        )
        mutate(
            "wrong-matching-gates",
            lambda data: (
                data["governance_execution"].__setitem__(
                    "immediate_next_gate", "ARBITRARY_AGREED_GATE"
                ),
                data["governance"].__setitem__("next_gate", "ARBITRARY_AGREED_GATE"),
            ),
        )
        mutate(
            "gate-mismatch",
            lambda data: data["governance_execution"].__setitem__(
                "immediate_next_gate", "INVALID_GATE"
            ),
        )
        mutate(
            "phase19-started",
            lambda data: data["roadmap_22"].__setitem__("phase_19_started", True),
        )
        mutate(
            "accepted-sha-drift",
            lambda data: data["repository"]["accepted_implementation"].__setitem__(
                "sha", "0" * 40
            ),
        )
        mutate(
            "accepted-tree-drift",
            lambda data: data["repository"]["accepted_implementation"].__setitem__(
                "tree", "0" * 40
            ),
        )
        mutate(
            "accepted-milestone-drift",
            lambda data: data["repository"]["accepted_implementation"].__setitem__(
                "milestone", "ROADMAP_22_PHASE_17_SANDBOX_ISOLATION"
            ),
        )
        mutate(
            "accepted-remote-reachability-drift",
            lambda data: data["repository"]["accepted_implementation"].__setitem__(
                "accepted_implementation_remote_reachable", False
            ),
        )
        mutate(
            "phase18-decision-recovery-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_faof_2_decision_recovery", "IN_PROGRESS"
            ),
        )
        mutate(
            "bounded-acceptance-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_faof_2_bounded_implementation_acceptance", "PENDING"
            ),
        )
        mutate(
            "phase19-authorization-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_19_implementation_authorization", "AUTHORIZED"
            ),
        )
        mutate(
            "phase18-integration-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_canonical_main_integration", "FAST_FORWARDED"
            ),
        )
        mutate(
            "next-roadmap-execution-started",
            lambda data: data["governance_execution"][
                "next_roadmap_execution_after_governance_gate"
            ].__setitem__("started", True),
        )
        mutate(
            "roadmap23-started",
            lambda data: data["roadmap_23"].__setitem__("status", "IN_PROGRESS"),
        )
        mutate(
            "canonical-main-sha-drift",
            lambda data: data["repository"]["canonical_main"].__setitem__(
                "sha", "0" * 40
            ),
        )

        for name, mutation_state in state_mutations:
            write_state(state, mutation_state)
            expect_red(name, invoke(doc, ledger, state, tracks, closure))
            red_count += 1
        write_state(state, baseline_state)

        tracks_data = yaml.safe_load(TRACKS.read_text())
        execution_track = next(
            track
            for track in tracks_data["tracks"]
            if track["id"] == "EXECUTION_AND_PROVIDER_RUNTIME"
        )
        if execution_track["next_actions"][0] != CLOSURE_ACTION:
            raise SystemExit("baseline does not carry the exact Phase18 closure track action")
        for name, replacement in (
            (
                "old-track-action",
                "Phase 18 - FAOF-2 Formal Algorithm Validation Bounded Implementation "
                "(IN_PROGRESS; IMPLEMENTATION_AUTHORIZED; FINAL_REVIEW_PENDING)",
            ),
            (
                "altered-track-action",
                "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
                "(CLOSED; ACCEPTED; CANONICAL_MAIN_INTEGRATION_COMPLETE)",
            ),
        ):
            changed_tracks = copy.deepcopy(tracks_data)
            changed_execution = next(
                track
                for track in changed_tracks["tracks"]
                if track["id"] == "EXECUTION_AND_PROVIDER_RUNTIME"
            )
            changed_execution["next_actions"][0] = replacement
            tracks.write_text(yaml.safe_dump(changed_tracks, sort_keys=False))
            expect_red(name, invoke(doc, ledger, state, tracks, closure))
            red_count += 1

    print(
        "PHASE17_SANDBOX_LEDGER_RED_MATRIX=PASS "
        f"mutations={red_count} exact_phase18_closure_publication_pass=1 "
        "old_in_progress_red=1 wrong_gates_red=1 phase19_started_red=1 "
        "accepted_identity_red=1 altered_track_action_red=1"
    )


if __name__ == "__main__":
    main()
