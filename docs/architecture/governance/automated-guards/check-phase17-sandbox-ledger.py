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
CORRECTION_18_FCV_GATE = "CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_BOUNDED_IMPLEMENTATION_CORRECTION_18_FCV_REVIEW"
PHASE17_CLOSURE_GATE = "CHATGPT_ROADMAP_22_PHASE_17_CANONICAL_INTEGRATION_AUTHORIZATION"
PHASE17_POST_INTEGRATION_GATE = "CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1_FINAL_REVIEW"
PHASE18_DECISION_RECOVERY_GATE = "CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_FINAL_REVIEW"
PHASE18_DECISION_RECOVERY_CORRECTION_1_GATE = "CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_CORRECTION_1_FINAL_REVIEW"
PHASE18_BOUNDED_IMPLEMENTATION_GATE = "CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_BOUNDED_IMPLEMENTATION_REVIEW"
PHASE18_BOUNDED_IMPLEMENTATION_ACTION = (
    "Phase 18 - FAOF-2 Formal Algorithm Validation Bounded Implementation "
    "(IN_PROGRESS; IMPLEMENTATION_AUTHORIZED; FINAL_REVIEW_PENDING)"
)
PHASE18_CLOSURE_GATE = (
    "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_AUTHORIZED_PENDING"
)
PHASE18_CLOSURE_ACTION = (
    "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
    "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_AUTHORIZED_PENDING)"
)
PHASE18_POST_INTEGRATION_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_AUTHORIZATION"
)
PHASE18_POST_INTEGRATION_ACTION = (
    "Phase 18 - FAOF-2 Formal Algorithm Validation Closure Publication "
    "(CLOSED; ACCEPTED; CANONICAL_MAIN_FF_ONLY_INTEGRATION_COMPLETE)"
)
PHASE19_CONDITIONAL_ACTION = (
    "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
    "FFmpeg CPU Native Pull Provider vertical slice (NOT_STARTED; "
    "IMPLEMENTATION_AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION)"
)
PHASE19_ACTION = (
    "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
    "FFmpeg CPU Native Pull Provider vertical slice (NOT_STARTED; "
    "IMPLEMENTATION_AUTHORIZED)"
)
PHASE19_IMPLEMENTATION_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_FINAL_REVIEW"
)
PHASE19_IMPLEMENTATION_ACTION = (
    "Phase 19 - blocking WorkerRuntime Support Advertisement plus first real "
    "FFmpeg CPU Native Pull Provider vertical slice "
    "(IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW)"
)
PHASE19_CONDITIONAL_AUTHORIZATION = (
    "AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION"
)
PHASE19_AUTHORIZATION = "AUTHORIZED"
ACCEPTED_PHASE18_IMPLEMENTATION = {
    "milestone": "ROADMAP_22_PHASE_18_FAOF_2",
    "sha": "f00c0f36f7686314f6bb75a6b414751f66b95f9a",
    "tree": "4b2ccb4c1161d1c4517a1d71b17616e6d8198595",
    "accepted_implementation_remote_reachable": True,
}
EXPECTED_PRE_INTEGRATION_MAIN = {
    "sha": "d2cc856939fe0a73d6f1ef799078a0a5e7c5b179",
    "tree": "d2e68f5af848cb49a5db1ea33cd8629ad5b250e0",
}
EXPECTED_POST_INTEGRATION_MAIN = {
    "sha": "ef0de1ed02147a701c649be7e4c7ebd0987bbea9",
    "tree": "34765d742ccc37d215ee800d0c203f584649049e",
}
EXPECTED_PHASE18_PRE_INTEGRATION_MAIN = (
    "bb4c683d11f6fb866c64f5d68ca81be79985bfdb"
)
EXPECTED_PHASE18_POST_INTEGRATION_MAIN = {
    "sha": "c15751ee625248160dbd899a5f79172578619961",
    "tree": "df93f7fb95d3dcd09132794b986aa3a995d8cdc1",
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
    ABSENT,
    ABSENT,
    ABSENT,
    ABSENT,
    ABSENT,
)
CORRECTION_15_FROZEN_CANDIDATE = (
    True,
    "CLOSED",
    "FROZEN_CANDIDATE_PENDING_FCV",
    False,
    False,
    CORRECTION_18_FCV_GATE,
    CORRECTION_18_FCV_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "IN_PROGRESS",
    ABSENT,
    ABSENT,
    ABSENT,
    ABSENT,
)
PRE_INTEGRATION_PHASE17_CLOSED = (
    True,
    "CLOSED",
    "CLOSED",
    False,
    False,
    PHASE17_CLOSURE_GATE,
    PHASE17_CLOSURE_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    ABSENT,
    ABSENT,
    ABSENT,
    ABSENT,
)
PHASE18_DECISION_RECOVERY = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    False,
    PHASE18_DECISION_RECOVERY_GATE,
    PHASE18_DECISION_RECOVERY_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    ABSENT,
    "FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW",
    ABSENT,
    False,
)
PHASE18_DECISION_RECOVERY_CORRECTION_1 = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    False,
    PHASE18_DECISION_RECOVERY_CORRECTION_1_GATE,
    PHASE18_DECISION_RECOVERY_CORRECTION_1_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    ABSENT,
    "CORRECTION_1_FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW",
    ABSENT,
    False,
)
PHASE18_BOUNDED_IMPLEMENTATION = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    False,
    PHASE18_BOUNDED_IMPLEMENTATION_GATE,
    PHASE18_BOUNDED_IMPLEMENTATION_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    "IN_PROGRESS",
    "PASS",
    "IN_PROGRESS",
    True,
)
PHASE18_CLOSURE_PUBLICATION = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    False,
    PHASE18_CLOSURE_GATE,
    PHASE18_CLOSURE_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    "CLOSED",
    "PASS",
    "CLOSED",
    True,
)
PHASE18_POST_INTEGRATION = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    False,
    PHASE18_POST_INTEGRATION_GATE,
    PHASE18_POST_INTEGRATION_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    "CLOSED",
    "PASS",
    "CLOSED",
    True,
)
PHASE19_IMPLEMENTATION_CANDIDATE = (
    True,
    "CLOSED",
    "CLOSED",
    True,
    True,
    PHASE19_IMPLEMENTATION_GATE,
    PHASE19_IMPLEMENTATION_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    "CLOSED",
    "PASS",
    "CLOSED",
    True,
)
POST_INTEGRATION_PHASE17_CLOSED = (
    True,
    "CLOSED",
    "CLOSED",
    False,
    False,
    PHASE17_POST_INTEGRATION_GATE,
    PHASE17_POST_INTEGRATION_GATE,
    "NOT_STARTED",
    "ADOPTED_DEFERRED",
    "CLOSED",
    ABSENT,
    ABSENT,
    ABSENT,
    ABSENT,
)
ACCEPTED_PHASE_STATES = {
    PHASE18_CLOSURE_PUBLICATION,
    PHASE18_POST_INTEGRATION,
    PHASE19_IMPLEMENTATION_CANDIDATE,
}

def fail(msg: str) -> None:
    print(f"FAIL: {msg}", file=sys.stderr)
    raise SystemExit(1)

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--document", default="docs/architecture/governance/roadmap-22-phase-17-sandbox-isolation-decision-recovery.md")
    ap.add_argument("--ledger", default="docs/architecture/governance/automated-guards/phase17-sandbox-isolation-clean-forward-ledger.tsv")
    ap.add_argument("--state", default="docs/architecture/governance/project-state/current-state.yaml")
    ap.add_argument("--tracks", default="docs/architecture/governance/project-state/roadmap-tracks.yaml")
    ap.add_argument(
        "--closure-publication",
        default="docs/architecture/governance/roadmap-22-phase-18-faof-2-closure-publication.md",
    )
    ap.add_argument(
        "--post-integration-governance",
        default="docs/architecture/governance/roadmap-22-phase-18-post-integration-governance.md",
    )
    args = ap.parse_args()
    doc_path, ledger_path, state_path, tracks_path, closure_path, post_integration_path = map(
        Path,
        (
            args.document,
            args.ledger,
            args.state,
            args.tracks,
            args.closure_publication,
            args.post_integration_governance,
        ),
    )
    for p in (
        doc_path,
        ledger_path,
        state_path,
        tracks_path,
        closure_path,
    ):
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
        roadmap_22.get("phase_17", ABSENT),
        roadmap_22.get("phase_18", ABSENT),
        roadmap_22.get("phase_18_faof_2_decision_recovery", ABSENT),
        roadmap_22.get("phase_18_faof_2_bounded_implementation", ABSENT),
        roadmap_22.get("phase_18_implementation_authorized", ABSENT),
    )
    if phase_state not in ACCEPTED_PHASE_STATES:
        fail("governance Phase 17 state does not match an accepted transition")
    repository = state.get("repository", {})
    post_integration_record = state.get("governance", {}).get(
        "phase_18_post_integration_governance_record", ABSENT
    )
    is_post_integration = post_integration_record != ABSENT
    if is_post_integration:
        if (
            post_integration_record
            != "docs/architecture/governance/roadmap-22-phase-18-post-integration-governance.md"
            or phase_state not in {
                PHASE18_POST_INTEGRATION, PHASE19_IMPLEMENTATION_CANDIDATE
            }
        ):
            fail("persisted post-integration baseline marker or transition tuple differs")
        if not post_integration_path.is_file():
            fail(f"missing required file: {post_integration_path}")
        if canonical_main != EXPECTED_PHASE18_POST_INTEGRATION_MAIN:
            fail("persisted canonical main differs from integrated Phase 18 closure publication")
    else:
        if phase_state != PHASE18_CLOSURE_PUBLICATION:
            fail("historical fixture does not match the accepted closure-publication tuple")
        if canonical_main != EXPECTED_POST_INTEGRATION_MAIN:
            fail("historical canonical main differs from accepted Phase 17 source tip")
    active_governed_branch = repository.get("active_governed_branch", {}).get(
        "name", ABSENT
    )
    if active_governed_branch != "main":
        fail("persisted active governed branch differs from main")
    if repository.get("accepted_implementation") != ACCEPTED_PHASE18_IMPLEMENTATION:
        fail("accepted Phase 18 implementation identity differs from final-review PASS")
    expected_canonical_source = (
        EXPECTED_PHASE18_POST_INTEGRATION_MAIN["sha"]
        if is_post_integration
        else EXPECTED_POST_INTEGRATION_MAIN["sha"]
    )
    if roadmap_22.get("canonical_main_integration_source_tip") != expected_canonical_source:
        fail("persisted canonical integration source tip is stale")

    expected_phase18_state = {
        "phase_18_faof_2_bounded_implementation_acceptance": "ACCEPTED",
        "phase_18_final_validated_tip": ACCEPTED_PHASE18_IMPLEMENTATION["sha"],
        "phase_18_final_validated_tree": ACCEPTED_PHASE18_IMPLEMENTATION["tree"],
        "phase_18_final_review": "PASS",
        "phase_18_standard_ci_run": 33064958899,
        "phase_18_standard_ci_status": "completed/success",
        "phase_18_foundation_verification_run": 33064958805,
        "phase_18_foundation_verification_status": "completed/success",
    }
    if is_post_integration:
        expected_phase18_state.update({
        "phase_18_closure_publication_sha": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["sha"],
        "phase_18_closure_publication_tree": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["tree"],
        "phase_18_closure_publication_standard_ci_run": 33068621878,
        "phase_18_closure_publication_standard_ci_status": "completed/success",
        "phase_18_closure_publication_foundation_verification_run": 33068621876,
        "phase_18_closure_publication_foundation_verification_status": "completed/success",
        "phase_18_canonical_main_pre_integration_sha": EXPECTED_PHASE18_PRE_INTEGRATION_MAIN,
        "phase_18_canonical_main_integration": "COMPLETED_FAST_FORWARD_ONLY",
        "phase_18_canonical_main_integration_source_tip": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["sha"],
        "phase_18_canonical_main_integration_source_tree": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["tree"],
        "phase_18_post_integration_standard_ci_run": 33070334626,
        "phase_18_post_integration_standard_ci_status": "completed/success",
        "phase_18_post_integration_foundation_verification_run": 33070334585,
        "phase_18_post_integration_foundation_verification_status": "completed/success",
        "phase_19_implementation_authorization": PHASE19_AUTHORIZATION,
        "faof_3": "NOT_AUTHORIZED",
        })
    else:
        expected_phase18_state.update({
            "phase_18_canonical_main_integration": "AUTHORIZED_PENDING_FAST_FORWARD_ONLY",
            "phase_18_canonical_main_integration_source_tip": ACCEPTED_PHASE18_IMPLEMENTATION["sha"],
            "phase_18_canonical_main_integration_source_tree": ACCEPTED_PHASE18_IMPLEMENTATION["tree"],
            "phase_19_implementation_authorization": PHASE19_CONDITIONAL_AUTHORIZATION,
        })
    for key, expected in expected_phase18_state.items():
        if roadmap_22.get(key, ABSENT) != expected:
            fail(f"persisted Phase 18 closure field differs: {key}")

    phase19_started = phase_state == PHASE19_IMPLEMENTATION_CANDIDATE
    expected_next_execution = {
        "roadmap": 22,
        "phase": 19,
        "started": phase19_started,
        "implementation_authorized": is_post_integration,
        "authorization_condition": (
            "SATISFIED_BY_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION"
            if is_post_integration
            else PHASE19_CONDITIONAL_AUTHORIZATION
        ),
        "topic": [
            "WorkerRuntime Support Advertisement",
            "First real FFmpeg CPU Native Pull Provider vertical slice",
        ],
    }
    if phase19_started:
        expected_next_execution["implementation_status"] = (
            "IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW"
        )
    if (
        governance_execution.get("next_roadmap_execution_after_governance_gate")
        != expected_next_execution
    ):
        fail("persisted post-closure roadmap execution condition is stale")

    closure = closure_path.read_text()
    expected_closure_facts = {
        "ACCEPTED_IMPLEMENTATION_SHA": ACCEPTED_PHASE18_IMPLEMENTATION["sha"],
        "ACCEPTED_IMPLEMENTATION_TREE": ACCEPTED_PHASE18_IMPLEMENTATION["tree"],
        "PUBLICATION_PARENT": ACCEPTED_PHASE18_IMPLEMENTATION["sha"],
        "CHATGPT_FINAL_REVIEW": "PASS",
        "LOCAL_VALIDATION": "PASS",
        "REMOTE_VALIDATION": "PASS",
        "STANDARD_CI_RUN": "33064958899",
        "STANDARD_CI_RESULT": "completed/success",
        "FOUNDATION_VERIFICATION_RUN": "33064958805",
        "FOUNDATION_VERIFICATION_RESULT": "completed/success",
        "PHASE_18": "CLOSED",
        "PHASE_18_DECISION_RECOVERY": "PASS",
        "PHASE_18_BOUNDED_IMPLEMENTATION": "CLOSED/ACCEPTED",
        "CLOSURE_PUBLICATION": "PUBLISHED_PENDING_CANONICAL_INTEGRATION",
        "CANONICAL_MAIN_FAST_FORWARD_INTEGRATION": "AUTHORIZED_PENDING",
        "FAST_FORWARD_ONLY": "YES",
        "MERGE_COMMIT": "PROHIBITED",
        "HISTORY_REWRITE": "PROHIBITED",
        "PHASE_19_STARTED": "NO",
        "PHASE_19_IMPLEMENTATION": PHASE19_CONDITIONAL_AUTHORIZATION,
        "ROADMAP_23": "NOT_STARTED",
    }
    for key, expected in expected_closure_facts.items():
        values = re.findall(rf"(?m)^{re.escape(key)}=(.+)$", closure)
        if values != [expected]:
            fail(f"closure publication fact differs: {key}")

    if is_post_integration:
        post_integration = post_integration_path.read_text()
        expected_post_integration_facts = {
        "PRE_INTEGRATION_MAIN": EXPECTED_PHASE18_PRE_INTEGRATION_MAIN,
        "CANONICAL_MAIN_INTEGRATION": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["sha"],
        "CANONICAL_MAIN_TREE": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["tree"],
        "INTEGRATION_METHOD": "FAST_FORWARD_ONLY",
        "ACCEPTED_IMPLEMENTATION_SHA": ACCEPTED_PHASE18_IMPLEMENTATION["sha"],
        "ACCEPTED_IMPLEMENTATION_TREE": ACCEPTED_PHASE18_IMPLEMENTATION["tree"],
        "ACCEPTED_IMPLEMENTATION_DISTINCT_AND_REACHABLE": "YES",
        "CLOSURE_PUBLICATION_SHA": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["sha"],
        "CLOSURE_PUBLICATION_TREE": EXPECTED_PHASE18_POST_INTEGRATION_MAIN["tree"],
        "BRANCH_PUBLICATION_STANDARD_CI_RUN": "33068621878",
        "BRANCH_PUBLICATION_STANDARD_CI_RESULT": "completed/success",
        "BRANCH_PUBLICATION_FOUNDATION_VERIFICATION_RUN": "33068621876",
        "BRANCH_PUBLICATION_FOUNDATION_VERIFICATION_RESULT": "completed/success",
        "POST_INTEGRATION_MAIN_STANDARD_CI_RUN": "33070334626",
        "POST_INTEGRATION_MAIN_STANDARD_CI_RESULT": "completed/success",
        "POST_INTEGRATION_MAIN_FOUNDATION_VERIFICATION_RUN": "33070334585",
        "POST_INTEGRATION_MAIN_FOUNDATION_VERIFICATION_RESULT": "completed/success",
        "PHASE_18": "CLOSED",
        "PHASE_18_BOUNDED_IMPLEMENTATION": "CLOSED/ACCEPTED",
        "PHASE_19_IMPLEMENTATION_AUTHORIZATION": PHASE19_AUTHORIZATION,
        "PHASE_19_STARTED": "NO",
        "FAOF_3": "NOT_AUTHORIZED",
        "ROADMAP_23": "NOT_STARTED",
        "NEXT_GATE": PHASE18_POST_INTEGRATION_GATE,
        }
        for key, expected in expected_post_integration_facts.items():
            values = re.findall(rf"(?m)^{re.escape(key)}=(.+)$", post_integration)
            if values != [expected]:
                fail(f"post-integration governance fact differs: {key}")

    tracks = yaml.safe_load(tracks_path.read_text())
    if not isinstance(tracks, dict) or not isinstance(tracks.get("tracks"), list):
        fail("roadmap tracks document is not a track list")
    execution_track = next(
        (
            track
            for track in tracks["tracks"]
            if isinstance(track, dict)
            and track.get("id") == "EXECUTION_AND_PROVIDER_RUNTIME"
        ),
        None,
    )
    if execution_track is None:
        fail("roadmap track EXECUTION_AND_PROVIDER_RUNTIME is missing")
    actions = execution_track.get("next_actions")
    dependencies = execution_track.get("current_dependencies", "")
    expected_phase18_action = (
        PHASE18_POST_INTEGRATION_ACTION if is_post_integration else PHASE18_CLOSURE_ACTION
    )
    expected_phase19_action = (
        PHASE19_IMPLEMENTATION_ACTION
        if phase19_started
        else PHASE19_ACTION if is_post_integration else PHASE19_CONDITIONAL_ACTION
    )
    expected_dependency = (
        "Phase 18 FAOF-2 CLOSED/ACCEPTED and integrated"
        if is_post_integration
        else "Phase 18 FAOF-2 CLOSED/ACCEPTED"
    )
    if (
        not isinstance(actions, list)
        or len(actions) < 2
        or actions[0] != expected_phase18_action
        or actions[1] != expected_phase19_action
        or expected_dependency not in dependencies
    ):
        fail("persisted roadmap track does not match exact Phase 18 post-integration action")
    if not is_post_integration:
        print(
            f"PHASE17_SANDBOX_LEDGER_GUARD=PASS rows={len(rows)} "
            f"dispositions={dict(sorted(counts.items()))} unclassified={unclassified} "
            f"duplicates={duplicate_count} placeholders=0 globs=0"
        )
        return
    formal_track = next(
        (
            track
            for track in tracks["tracks"]
            if isinstance(track, dict) and track.get("id") == "FORMAL_VERIFICATION_TRACK"
        ),
        None,
    )
    expected_formal_action = (
        "Phase 19 - review the bounded FFmpeg CPU Native Pull Provider implementation "
        "candidate; do not claim Phase 19 closed"
        if phase19_started
        else "Phase 19 - use the authorized-not-started implementation planning/start gate; "
        "do not claim implementation started"
    )
    expected_phase19_dependency = (
        "Phase 19 implementation candidate is pending ChatGPT final review"
        if phase19_started
        else "Phase 19 implementation is AUTHORIZED but NOT_STARTED"
    )
    if (
        formal_track is None
        or formal_track.get("next_actions", [ABSENT])[0] != expected_formal_action
        or "Phase 18 FAOF-2 bounded implementation is CLOSED/ACCEPTED and integrated"
        not in formal_track.get("current_dependencies", "")
        or expected_phase19_dependency
        not in formal_track.get("current_dependencies", "")
        or "FAOF-3 is NOT_AUTHORIZED"
        not in formal_track.get("current_dependencies", "")
    ):
        fail("persisted formal verification track is stale")
    print(
        f"PHASE17_SANDBOX_LEDGER_GUARD=PASS rows={len(rows)} "
        f"dispositions={dict(sorted(counts.items()))} unclassified={unclassified} "
        f"duplicates={duplicate_count} placeholders=0 globs=0")

if __name__ == "__main__":
    main()
