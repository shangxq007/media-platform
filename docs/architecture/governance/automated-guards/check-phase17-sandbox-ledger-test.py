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
POST_INTEGRATION = ROOT / "docs/architecture/governance/roadmap-22-phase-18-post-integration-governance.md"

ACCEPTED_SHA = "f00c0f36f7686314f6bb75a6b414751f66b95f9a"
ACCEPTED_TREE = "4b2ccb4c1161d1c4517a1d71b17616e6d8198595"
INTEGRATED_SHA = "c15751ee625248160dbd899a5f79172578619961"
INTEGRATED_TREE = "df93f7fb95d3dcd09132794b986aa3a995d8cdc1"
PRE_INTEGRATION_MAIN = "bb4c683d11f6fb866c64f5d68ca81be79985bfdb"
POST_INTEGRATION_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_AUTHORIZATION"
)
POST_INTEGRATION_ACTION = (
    "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
    "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_COMPLETE)"
)
PHASE19_ACTION = (
    "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
    "FFmpeg CPU Native Pull Provider vertical slice (NOT_STARTED; "
    "IMPLEMENTATION_AUTHORIZED)"
)
PHASE19_AUTHORIZATION = "AUTHORIZED"
FORMAL_TRACK_ACTION = (
    "Phase 19 - use the authorized-not-started implementation planning/start gate; "
    "do not claim implementation started"
)


def invoke(doc, ledger, state, tracks, closure, post_integration):
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
            "--post-integration-governance",
            str(post_integration),
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
        or roadmap_22.get("phase_18_canonical_main_integration")
        != "COMPLETED_FAST_FORWARD_ONLY"
        or roadmap_22.get("phase_18_canonical_main_integration_source_tip")
        != INTEGRATED_SHA
        or roadmap_22.get("phase_18_canonical_main_integration_source_tree")
        != INTEGRATED_TREE
        or roadmap_22.get("faof_3") != "NOT_AUTHORIZED"
        or governance_execution.get("immediate_next_gate") != POST_INTEGRATION_GATE
        or baseline_state.get("governance", {}).get("next_gate")
        != POST_INTEGRATION_GATE
        or baseline_state.get("roadmap_23", {}).get("status") != "NOT_STARTED"
        or baseline_state.get("repository", {}).get("canonical_main")
        != {"sha": INTEGRATED_SHA, "tree": INTEGRATED_TREE}
    ):
        raise SystemExit("baseline does not carry the exact Phase18 post-integration state")

    with tempfile.TemporaryDirectory(prefix="phase17-ledger-red-") as directory:
        root = Path(directory)
        doc = root / "doc.md"
        ledger = root / "ledger.tsv"
        state = root / "state.yaml"
        tracks = root / "tracks.yaml"
        closure = root / "closure.md"
        post_integration = root / "post-integration.md"
        doc.write_text(DOC.read_text())
        write_rows(ledger, fields, rows)
        write_state(state, baseline_state)
        tracks.write_text(TRACKS.read_text())
        closure.write_text(CLOSURE.read_text())
        post_integration.write_text(POST_INTEGRATION.read_text())

        baseline = invoke(doc, ledger, state, tracks, closure, post_integration)
        if baseline.returncode != 0:
            raise SystemExit(
                "exact Phase18 post-integration baseline did not pass: "
                + baseline.stderr.decode()
            )

        historical_state = copy.deepcopy(baseline_state)
        historical_state["repository"]["canonical_main"] = {
            "sha": "ef0de1ed02147a701c649be7e4c7ebd0987bbea9",
            "tree": "34765d742ccc37d215ee800d0c203f584649049e",
        }
        historical_roadmap = historical_state["roadmap_22"]
        for key in (
            "phase_18_closure_publication_sha",
            "phase_18_closure_publication_tree",
            "phase_18_closure_publication_standard_ci_run",
            "phase_18_closure_publication_standard_ci_status",
            "phase_18_closure_publication_foundation_verification_run",
            "phase_18_closure_publication_foundation_verification_status",
            "phase_18_canonical_main_pre_integration_sha",
            "phase_18_post_integration_standard_ci_run",
            "phase_18_post_integration_standard_ci_status",
            "phase_18_post_integration_foundation_verification_run",
            "phase_18_post_integration_foundation_verification_status",
            "faof_3",
        ):
            historical_roadmap.pop(key)
        historical_roadmap.update(
            {
                "phase_18_canonical_main_integration": "AUTHORIZED_PENDING_FAST_FORWARD_ONLY",
                "phase_18_canonical_main_integration_source_tip": ACCEPTED_SHA,
                "phase_18_canonical_main_integration_source_tree": ACCEPTED_TREE,
                "phase_19_implementation_authorization": (
                    "AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION"
                ),
                "canonical_main_integration_source_tip": (
                    "ef0de1ed02147a701c649be7e4c7ebd0987bbea9"
                ),
            }
        )
        historical_gate = (
            "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_"
            "AUTHORIZED_PENDING"
        )
        historical_state["governance_execution"]["immediate_next_gate"] = historical_gate
        historical_next = historical_state["governance_execution"][
            "next_roadmap_execution_after_governance_gate"
        ]
        historical_next["implementation_authorized"] = False
        historical_next["authorization_condition"] = (
            "AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION"
        )
        historical_state["governance"]["next_gate"] = historical_gate
        historical_state["governance"].pop(
            "phase_18_post_integration_governance_record"
        )
        write_state(state, historical_state)
        historical_tracks = yaml.safe_load(TRACKS.read_text())
        historical_execution = next(
            track
            for track in historical_tracks["tracks"]
            if track["id"] == "EXECUTION_AND_PROVIDER_RUNTIME"
        )
        historical_execution["current_dependencies"] = (
            "Phase 18 FAOF-2 CLOSED/ACCEPTED pending canonical-main "
            "fast-forward-only integration"
        )
        historical_execution["next_actions"][0] = (
            "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
            "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_AUTHORIZED_PENDING)"
        )
        historical_execution["next_actions"][1] = (
            "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
            "FFmpeg CPU Native Pull Provider vertical slice (NOT_STARTED; "
            "IMPLEMENTATION_AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION)"
        )
        tracks.write_text(yaml.safe_dump(historical_tracks, sort_keys=False))
        historical = invoke(
            doc, ledger, state, tracks, closure, post_integration
        )
        if historical.returncode != 0:
            raise SystemExit(
                "historical Phase18 closure-publication fixture did not pass: "
                + historical.stderr.decode()
            )
        write_state(state, baseline_state)
        tracks.write_text(TRACKS.read_text())

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
            expect_red(name, invoke(doc, ledger, state, tracks, closure, post_integration))
            red_count += 1
        write_rows(ledger, fields, rows)

        doc.write_text(DOC.read_text().replace("TOTAL_ROWS=131", "TOTAL_ROWS=130"))
        expect_red("count-mismatch", invoke(doc, ledger, state, tracks, closure, post_integration))
        red_count += 1
        doc.write_text(DOC.read_text())

        closure_text = CLOSURE.read_text()
        for name, old, new in (
            ("closure-accepted-sha", ACCEPTED_SHA, "0" * 40),
            ("closure-accepted-tree", ACCEPTED_TREE, "0" * 40),
            ("closure-final-review", "CHATGPT_FINAL_REVIEW=PASS", "CHATGPT_FINAL_REVIEW=FAIL"),
            ("closure-ci", "STANDARD_CI_RUN=33064958899", "STANDARD_CI_RUN=0"),
            ("closure-foundation", "FOUNDATION_VERIFICATION_RUN=33064958805", "FOUNDATION_VERIFICATION_RUN=0"),
            (
                "closure-phase19",
                "PHASE_19_IMPLEMENTATION=AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION",
                "PHASE_19_IMPLEMENTATION=STARTED",
            ),
        ):
            closure.write_text(closure_text.replace(old, new))
            expect_red(name, invoke(doc, ledger, state, tracks, closure, post_integration))
            red_count += 1
        closure.write_text(closure_text)

        post_integration_text = POST_INTEGRATION.read_text()
        for name, old, new in (
            ("post-integration-main-sha", INTEGRATED_SHA, "2" * 40),
            ("post-integration-main-tree", INTEGRATED_TREE, "3" * 40),
            ("post-integration-accepted-sha", ACCEPTED_SHA, "4" * 40),
            ("post-integration-accepted-tree", ACCEPTED_TREE, "5" * 40),
            (
                "post-integration-pre-main",
                f"PRE_INTEGRATION_MAIN={PRE_INTEGRATION_MAIN}",
                f"PRE_INTEGRATION_MAIN={'6' * 40}",
            ),
            (
                "post-integration-branch-ci",
                "BRANCH_PUBLICATION_STANDARD_CI_RUN=33068621878",
                "BRANCH_PUBLICATION_STANDARD_CI_RUN=0",
            ),
            (
                "post-integration-main-foundation",
                "POST_INTEGRATION_MAIN_FOUNDATION_VERIFICATION_RUN=33070334585",
                "POST_INTEGRATION_MAIN_FOUNDATION_VERIFICATION_RUN=0",
            ),
            (
                "post-integration-phase19-authorization",
                "PHASE_19_IMPLEMENTATION_AUTHORIZATION=AUTHORIZED",
                "PHASE_19_IMPLEMENTATION_AUTHORIZATION=NOT_AUTHORIZED",
            ),
            (
                "post-integration-phase19-started",
                "PHASE_19_STARTED=NO",
                "PHASE_19_STARTED=YES",
            ),
            (
                "post-integration-next-gate",
                f"NEXT_GATE={POST_INTEGRATION_GATE}",
                "NEXT_GATE=INTEGRATION_PENDING",
            ),
        ):
            post_integration.write_text(post_integration_text.replace(old, new))
            expect_red(name, invoke(doc, ledger, state, tracks, closure, post_integration))
            red_count += 1
        post_integration.write_text(post_integration_text)

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
                    "immediate_next_gate",
                    "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_AUTHORIZED_PENDING",
                ),
                data["governance"].__setitem__(
                    "next_gate",
                    "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_AUTHORIZED_PENDING",
                ),
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
                "phase_19_implementation_authorization", "NOT_AUTHORIZED"
            ),
        )
        mutate(
            "phase18-integration-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_canonical_main_integration",
                "AUTHORIZED_PENDING_FAST_FORWARD_ONLY",
            ),
        )
        mutate(
            "integration-source-sha-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_canonical_main_integration_source_tip", "7" * 40
            ),
        )
        mutate(
            "integration-source-tree-drift",
            lambda data: data["roadmap_22"].__setitem__(
                "phase_18_canonical_main_integration_source_tree", "8" * 40
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
        mutate(
            "canonical-main-tree-drift",
            lambda data: data["repository"]["canonical_main"].__setitem__(
                "tree", "9" * 40
            ),
        )
        mutate(
            "faof3-authorized",
            lambda data: data["roadmap_22"].__setitem__("faof_3", "AUTHORIZED"),
        )

        for name, mutation_state in state_mutations:
            write_state(state, mutation_state)
            expect_red(name, invoke(doc, ledger, state, tracks, closure, post_integration))
            red_count += 1
        write_state(state, baseline_state)

        tracks_data = yaml.safe_load(TRACKS.read_text())
        execution_track = next(
            track
            for track in tracks_data["tracks"]
            if track["id"] == "EXECUTION_AND_PROVIDER_RUNTIME"
        )
        if (
            execution_track["next_actions"][:2]
            != [POST_INTEGRATION_ACTION, PHASE19_ACTION]
        ):
            raise SystemExit("baseline does not carry the exact Phase18 post-integration track actions")
        for name, replacement in (
            (
                "pending-integration-track-action",
                "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
                "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_AUTHORIZED_PENDING)",
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
            expect_red(name, invoke(doc, ledger, state, tracks, closure, post_integration))
            red_count += 1
        changed_tracks = copy.deepcopy(tracks_data)
        changed_execution = next(
            track
            for track in changed_tracks["tracks"]
            if track["id"] == "EXECUTION_AND_PROVIDER_RUNTIME"
        )
        changed_execution["next_actions"][1] = (
            "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
            "FFmpeg CPU Native Pull Provider vertical slice (IN_PROGRESS; "
            "IMPLEMENTATION_STARTED)"
        )
        tracks.write_text(yaml.safe_dump(changed_tracks, sort_keys=False))
        expect_red(
            "phase19-started-track-action",
            invoke(doc, ledger, state, tracks, closure, post_integration),
        )
        red_count += 1

        formal_track = next(
            track
            for track in tracks_data["tracks"]
            if track["id"] == "FORMAL_VERIFICATION_TRACK"
        )
        if formal_track["next_actions"][0] != FORMAL_TRACK_ACTION:
            raise SystemExit("baseline does not carry the exact formal track action")
        tracks.write_text(TRACKS.read_text())
        changed_tracks = copy.deepcopy(tracks_data)
        changed_formal = next(
            track
            for track in changed_tracks["tracks"]
            if track["id"] == "FORMAL_VERIFICATION_TRACK"
        )
        changed_formal["next_actions"][0] = (
            "Phase 18 - validate closure publication, then perform authorized "
            "canonical-main fast-forward-only integration"
        )
        tracks.write_text(yaml.safe_dump(changed_tracks, sort_keys=False))
        expect_red(
            "old-formal-track-action",
            invoke(doc, ledger, state, tracks, closure, post_integration),
        )
        red_count += 1

    print(
        "PHASE17_SANDBOX_LEDGER_RED_MATRIX=PASS "
        f"mutations={red_count} exact_phase18_post_integration_pass=1 "
        "historical_closure_fixture_pass=1 "
        "pending_integration_state_red=1 wrong_gates_red=1 phase19_started_red=1 "
        "phase19_authorization_red=1 integrated_identity_red=1 "
        "accepted_identity_red=1 track_actions_red=1"
    )


if __name__ == "__main__":
    main()
