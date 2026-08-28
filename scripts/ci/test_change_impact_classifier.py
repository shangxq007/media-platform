#!/usr/bin/env python3
"""Executable GREEN/RED policy matrix for the change-impact classifier."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True


ROOT = Path(__file__).resolve().parents[2]
CLASSIFIER_PATH = ROOT / "scripts/ci/change_impact_classifier.py"
STANDARD_CI = ROOT / ".github/workflows/ci.yml"
FOUNDATION_CI = ROOT / ".github/workflows/architecture-drift.yml"
SEMGREP_CI = ROOT / ".github/workflows/semgrep-architecture.yml"
AMENDMENT = ROOT / "docs/architecture/governance/change-impact-driven-ci-governance-amendment-1.md"
CORRECTION = ROOT / "docs/architecture/governance/change-impact-driven-ci-governance-amendment-1-correction-1.md"
PHASE18_DECISION_RECOVERY = ROOT / "docs/architecture/governance/roadmap-22-phase-18-faof-2-decision-recovery.md"
PHASE18_DECISION_RECOVERY_CORRECTION_1 = ROOT / "docs/architecture/governance/roadmap-22-phase-18-faof-2-decision-recovery-correction-1.md"
REGISTRY = ROOT / "docs/architecture/governance/project-state/architecture-registry.yaml"
CURRENT_STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"

PHASE18_ACCEPTED_SHA = "f00c0f36f7686314f6bb75a6b414751f66b95f9a"
PHASE18_ACCEPTED_TREE = "4b2ccb4c1161d1c4517a1d71b17616e6d8198595"
PHASE18_INTEGRATED_SHA = "c15751ee625248160dbd899a5f79172578619961"
PHASE18_INTEGRATED_TREE = "df93f7fb95d3dcd09132794b986aa3a995d8cdc1"
PHASE18_PRE_INTEGRATION_MAIN = "bb4c683d11f6fb866c64f5d68ca81be79985bfdb"
PHASE19_AUTHORIZATION_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_FINAL_REVIEW"
)

STABLE_POLICY_IDS = (
    "CI_CHANGE_IMPACT_CLASSIFICATION_IS_PLATFORM_OWNED_V1",
    "CHANGE_IMPACT_TAXONOMY_IS_EXHAUSTIVE_AND_UNKNOWN_FAILS_CLOSED_V1",
    "BACKEND_AND_FRONTEND_CI_ARE_INDEPENDENTLY_IMPACT_GATED_V1",
    "CI_POLICY_SUMMARY_IS_UNCONDITIONAL_V1",
    "RUNTIME_IMAGE_PUBLICATION_REQUIRES_RUNTIME_CONTAINER_OR_BUILD_INPUT_IMPACT_V1",
    "NON_RUNTIME_CHANGES_MUST_NOT_PUBLISH_RUNTIME_IMAGES_V1",
    "GITOPS_SEMGREP_AND_ARCHITECTURE_VALIDATION_ARE_TARGETED_V1",
    "CLASSIFIER_DEPENDENT_CHECKOUTS_USE_COMPLETE_CREDENTIAL_FREE_HISTORY_V1",
)

spec = importlib.util.spec_from_file_location("change_impact_classifier", CLASSIFIER_PATH)
assert spec and spec.loader
classifier = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = classifier
spec.loader.exec_module(classifier)


CASES = (
    ("governance", "docs/architecture/governance/example.md", {"governance"}, {"architecture_drift"}),
    ("docs", "docs/development/example.md", {"docs"}, set()),
    ("frontend", "frontend/src/example.ts", {"frontend"}, {"frontend_ci"}),
    ("backend-test", "render-module/src/test/java/ExampleTest.java", {"backend_test"}, {"backend_ci"}),
    ("backend-runtime", "render-module/src/main/java/Example.java", {"backend_runtime"}, {"backend_ci", "architecture_drift", "semgrep_validation", "runtime_image_publish"}),
    ("build-graph", "render-module/build.gradle.kts", {"build_graph"}, {"backend_ci", "architecture_drift", "semgrep_validation", "runtime_image_publish"}),
    ("container", "remote-render-worker/Dockerfile", {"container"}, {"backend_ci", "runtime_image_publish"}),
    ("root-dockerignore", ".dockerignore", {"container"}, {"backend_ci", "runtime_image_publish"}),
    ("gitops", "gitops/staging/deployment-api.yaml", {"gitops"}, {"gitops_validation"}),
    ("semgrep", ".semgrep/media-platform-architecture.yml", {"semgrep"}, {"semgrep_validation"}),
    ("formal-source", "formal/lean/Faof2Graph.lean", {"formal_verification"}, {"formal_verification"}),
    ("formal-script", "scripts/formal/validate-faof2.sh", {"formal_verification"}, {"formal_verification"}),
    ("workflow", ".github/workflows/ci.yml", {"workflow"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation", "formal_verification"}),
    ("ci-infrastructure", "scripts/ci/setup-test-runtime.sh", {"ci_infrastructure"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation", "formal_verification"}),
    ("unknown", "unowned/new-surface.xyz", {"unknown"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation", "formal_verification"}),
)


def assert_case(name: str, path: str, expected_categories: set[str], enabled: set[str]) -> None:
    result = classifier.Classification.from_paths([path], "matrix")
    actual_categories = set(result.categories)
    if not expected_categories <= actual_categories:
        raise AssertionError(f"{name}: categories {actual_categories} do not include {expected_categories}")
    policy = result.policy()
    for decision, value in policy.items():
        expected = decision in enabled
        if value != expected:
            raise AssertionError(f"{name}: {decision}={value}, expected {expected}")


def assert_workflow_contract(
    standard: str, foundation: str, standalone_semgrep_exists: bool = False
) -> None:
    classifier_command = "scripts/ci/change_impact_classifier.py"
    if classifier_command not in standard or classifier_command not in foundation:
        raise AssertionError("all classifier-authority workflows must use the platform classifier")
    if standalone_semgrep_exists:
        raise AssertionError("standalone Semgrep workflow remains as parallel execution authority")
    for name, workflow in (("standard", standard), ("foundation", foundation)):
        if "paths-ignore:" in workflow or "\n    paths:\n" in workflow:
            raise AssertionError(f"{name}: workflow retains an older path authority")
        if "dorny/paths-filter" in workflow:
            raise AssertionError(f"{name}: third-party path classification is parallel authority")
    if "if: needs.change-impact.outputs.backend_ci == 'true'" not in standard:
        raise AssertionError("Standard CI backend is not independently conditional")
    if "if: needs.change-impact.outputs.frontend_ci == 'true'" not in standard:
        raise AssertionError("Standard CI frontend is not independently conditional")
    if "if: needs.change-impact.outputs.formal_verification == 'true'" not in standard:
        raise AssertionError("Standard CI formal validation is not independently conditional")
    if "policy-summary:" not in standard or "if: always()" not in standard:
        raise AssertionError("Standard CI policy summary is not unconditional")
    image_gate = "needs.change-impact.outputs.runtime_image_publish == 'true'"
    if image_gate not in standard:
        raise AssertionError("runtime image publication is not classifier-gated")
    if "if: needs.change-impact.outputs.architecture_drift == 'true'" not in foundation:
        raise AssertionError("Foundation architecture drift is not independently conditional")
    if "if: needs.change-impact.outputs.backend_ci == 'true'" not in foundation:
        raise AssertionError("Foundation full backend is not independently conditional")
    standard_backend = standard.split("\n  backend:\n", 1)[1].split("\n  frontend:\n", 1)[0]
    foundation_backend = foundation.split("\n  foundation-verification:\n", 1)[1].split(
        "\n  foundation-policy-summary:\n", 1
    )[0]
    hosted_forbidden = (
        "run: bash scripts/ci/setup-test-runtime.sh",
        "./gradlew --no-daemon test",
        "./gradlew --no-daemon --max-workers=1 test",
    )
    if any(item in standard_backend for item in hosted_forbidden):
        raise AssertionError("Standard hosted backend retains delegated runtime validation")
    if any(item in foundation_backend for item in hosted_forbidden):
        raise AssertionError("Foundation hosted backend retains delegated runtime validation")
    standard_hosted_required = (
        "./gradlew --no-daemon pfirr1RemediationCheck",
        "./gradlew --no-daemon compileJava compileTestJava",
        "./gradlew --no-daemon :platform-app:bootJar -x test",
        "docker build -t media-platform:ci .",
    )
    foundation_hosted_required = (
        "bash -n scripts/verify-pfirr1-jooq-authority-fail-closed.sh && bash -n scripts/ci/setup-test-runtime.sh",
        "bash scripts/verify-pfirr1-jooq-authority-fail-closed.sh",
        "./gradlew --no-daemon pfirr1RemediationCheck",
        "./gradlew --no-daemon compileJava compileTestJava",
        "./gradlew --no-daemon :platform-app:bootJar -x test",
    )
    if any(item not in standard_backend for item in standard_hosted_required):
        raise AssertionError("Standard hosted backend lost bounded compile/build validation")
    if any(item not in foundation_backend for item in foundation_hosted_required):
        raise AssertionError("Foundation hosted backend lost bounded foundation validation")
    if standard.count("\n  semgrep:\n") != 1:
        raise AssertionError("Standard CI does not own exactly one Semgrep job")
    if standard.count("\n  formal-validation:\n") != 1:
        raise AssertionError("Standard CI does not own exactly one formal validation job")
    formal_job = standard.split("\n  formal-validation:\n", 1)[1].split("\n  semgrep:\n", 1)[0]
    required_formal_job_contract = (
        "needs: change-impact",
        "if: needs.change-impact.outputs.formal_verification == 'true'",
        "uses: actions/checkout@v4",
        "fetch-depth: 0",
        "persist-credentials: false",
        "scripts/formal/validate-faof2.sh",
        "lean-4.19.0-linux.tar.zst",
        "6fe3ce97a58f44e2b3567d455b994eacec5bfe9ae7774f2a573444480ba813fe",
        "b80d66c91b4da3a1b3c5d3e6672cf8f4ab72ed2f7a6a1f0cf7d3aef747cf6a4b",
    )
    if any(item not in formal_job for item in required_formal_job_contract):
        raise AssertionError("Standard CI formal validation job is not reproducibly pinned")
    semgrep_job = standard.split("\n  semgrep:\n", 1)[1].split("\n  policy-summary:\n", 1)[0]
    required_semgrep_job_contract = (
        "needs: change-impact",
        "if: needs.change-impact.outputs.semgrep_validation == 'true'",
        "uses: actions/checkout@v4",
        "fetch-depth: 0",
        "persist-credentials: false",
        "uses: semgrep/semgrep-action@v1",
        "config: .semgrep/media-platform-architecture.yml",
        "SEMGREP_RULES: .semgrep/media-platform-architecture.yml",
    )
    if any(item not in semgrep_job for item in required_semgrep_job_contract):
        raise AssertionError("Standard CI Semgrep job does not match the canonical targeted contract")
    required_semgrep_policy_contract = (
        "needs: [change-impact, backend, frontend, gitops-validation, formal-validation, semgrep]",
        "FORMAL_REQUIRED: ${{ needs.change-impact.outputs.formal_verification }}",
        "FORMAL_RESULT: ${{ needs['formal-validation'].result }}",
        "SEMGREP_REQUIRED: ${{ needs.change-impact.outputs.semgrep_validation }}",
        "SEMGREP_RESULT: ${{ needs.semgrep.result }}",
        'echo "- semgrep: required=$SEMGREP_REQUIRED result=$SEMGREP_RESULT"',
        'test "$result" = "success"',
        'test "$result" = "skipped"',
        'require_result "$SEMGREP_REQUIRED" "$SEMGREP_RESULT" semgrep',
        'require_result "$FORMAL_REQUIRED" "$FORMAL_RESULT" formal-validation',
    )
    if any(item not in standard for item in required_semgrep_policy_contract):
        raise AssertionError("Standard CI policy summary does not report and enforce Semgrep")
    for name, workflow in (("standard", standard), ("foundation", foundation)):
        checkout_count = workflow.count("uses: actions/checkout@v4")
        if workflow.count("fetch-depth: 0") < checkout_count:
            raise AssertionError(f"{name}: classifier-dependent checkout has shallow history")
        if workflow.count("persist-credentials: false") < checkout_count:
            raise AssertionError(f"{name}: classifier-dependent checkout persists credentials")


def assert_phase18_post_integration_state(state: str) -> None:
    accepted_identity = (
        "  accepted_implementation:\n"
        "    milestone: ROADMAP_22_PHASE_18_FAOF_2\n"
        f"    sha: {PHASE18_ACCEPTED_SHA}\n"
        f"    tree: {PHASE18_ACCEPTED_TREE}\n"
        "    accepted_implementation_remote_reachable: true\n"
    )
    required_state = (
        "  canonical_main:\n",
        f"    sha: {PHASE18_INTEGRATED_SHA}\n",
        f"    tree: {PHASE18_INTEGRATED_TREE}\n",
        "  phase_17: CLOSED\n",
        "  phase_18_started: true\n",
        "  phase_18: CLOSED\n",
        "  phase_18_faof_2_decision_recovery: PASS\n",
        "  phase_18_implementation_authorized: true\n",
        "  phase_18_faof_2_bounded_implementation: CLOSED\n",
        "  phase_18_faof_2_bounded_implementation_acceptance: ACCEPTED\n",
        f"  phase_18_final_validated_tip: {PHASE18_ACCEPTED_SHA}\n",
        f"  phase_18_final_validated_tree: {PHASE18_ACCEPTED_TREE}\n",
        "  phase_18_final_review: PASS\n",
        f"  phase_18_closure_publication_sha: {PHASE18_INTEGRATED_SHA}\n",
        f"  phase_18_closure_publication_tree: {PHASE18_INTEGRATED_TREE}\n",
        "  phase_18_closure_publication_standard_ci_run: 33068621878\n",
        "  phase_18_closure_publication_standard_ci_status: completed/success\n",
        "  phase_18_closure_publication_foundation_verification_run: 33068621876\n",
        "  phase_18_closure_publication_foundation_verification_status: completed/success\n",
        f"  phase_18_canonical_main_pre_integration_sha: {PHASE18_PRE_INTEGRATION_MAIN}\n",
        "  phase_18_canonical_main_integration: COMPLETED_FAST_FORWARD_ONLY\n",
        f"  phase_18_canonical_main_integration_source_tip: {PHASE18_INTEGRATED_SHA}\n",
        f"  phase_18_canonical_main_integration_source_tree: {PHASE18_INTEGRATED_TREE}\n",
        "  phase_18_post_integration_standard_ci_run: 33070334626\n",
        "  phase_18_post_integration_standard_ci_status: completed/success\n",
        "  phase_18_post_integration_foundation_verification_run: 33070334585\n",
        "  phase_18_post_integration_foundation_verification_status: completed/success\n",
        "  phase_19_started: true\n",
        "  phase_19_implementation_authorization: AUTHORIZED\n",
        "  phase_19: IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW\n",
        "  phase_19_ffmpeg_cpu_native_pull_provider_bounded_implementation: IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW\n",
        "  faof_3: NOT_AUTHORIZED\n",
        "roadmap_23:\n  status: NOT_STARTED\n",
        f"  immediate_next_gate: {PHASE19_AUTHORIZATION_GATE}\n",
        f"  next_gate: {PHASE19_AUTHORIZATION_GATE}\n",
        "    phase: 19\n",
        "    started: true\n",
        "    implementation_authorized: true\n",
        "    implementation_status: IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW\n",
        "    authorization_condition: SATISFIED_BY_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION\n",
        "  phase_18_faof_2_bounded_implementation_record: docs/architecture/governance/roadmap-22-phase-18-faof-2-bounded-implementation.md\n",
        "  phase_18_faof_2_closure_publication_record: docs/architecture/governance/roadmap-22-phase-18-faof-2-closure-publication.md\n",
        "  phase_18_post_integration_governance_record: docs/architecture/governance/roadmap-22-phase-18-post-integration-governance.md\n",
        "  phase_19_ffmpeg_cpu_native_pull_provider_bounded_implementation_record: docs/architecture/governance/roadmap-22-phase-19-ffmpeg-cpu-native-pull-provider-bounded-implementation.md\n",
    )
    if state.count(accepted_identity) != 1 or any(
        state.count(item) != 1 for item in required_state
    ):
        raise AssertionError("Phase 18 FAOF-2 post-integration state drifted")
    forbidden_state = (
        "  phase_18: IN_PROGRESS\n",
        "  phase_18_faof_2_bounded_implementation: IN_PROGRESS\n",
        "  phase_19_started: false\n",
        "  phase_18_canonical_main_integration: AUTHORIZED_PENDING_FAST_FORWARD_ONLY\n",
        "  phase_19_implementation_authorization: AUTHORIZED_ONLY_AFTER_SUCCESSFUL_PHASE18_CANONICAL_INTEGRATION\n",
        "ROADMAP_22_PHASE_18_CANONICAL_MAIN_FAST_FORWARD_INTEGRATION_AUTHORIZED_PENDING",
        "CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_BOUNDED_IMPLEMENTATION_REVIEW",
    )
    if any(item in state for item in forbidden_state):
        raise AssertionError("stale or unauthorized Phase 18 lifecycle state remains")


def assert_governance_contract() -> None:
    amendment = AMENDMENT.read_text()
    correction = CORRECTION.read_text()
    registry = REGISTRY.read_text()
    state = CURRENT_STATE.read_text()
    for stable_id in STABLE_POLICY_IDS:
        if amendment.count(f"`{stable_id}`") != 1:
            raise AssertionError(f"amendment stable ID is missing or duplicated: {stable_id}")
        if registry.count(f"- id: {stable_id}\n") != 1:
            raise AssertionError(f"registry stable ID is missing or duplicated: {stable_id}")
        registry_entry = registry.split(f"- id: {stable_id}\n", 1)[1]
        if not registry_entry.startswith("  status: ADOPTED\n"):
            raise AssertionError(f"registry stable ID is not adopted: {stable_id}")
    phase18_recovery = PHASE18_DECISION_RECOVERY.read_text()
    phase18_correction = PHASE18_DECISION_RECOVERY_CORRECTION_1.read_text()
    assert_phase18_post_integration_state(state)
    if "ROADMAP_22_PHASE_18_FAOF_2_BOUNDED_ARCHITECTURE_CONTRACT_V1" not in phase18_recovery:
        raise AssertionError("Phase18 Decision Recovery contract is missing")
    if "ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_CORRECTION_1_CONTRACT_V1" not in phase18_correction:
        raise AssertionError("Phase18 Decision Recovery Correction 1 contract is missing")
    if ("    change_impact_driven_ci_governance: ADOPTED\n" not in state
            or "  change_impact_driven_ci_governance_amendment: docs/architecture/governance/change-impact-driven-ci-governance-amendment-1.md\n" not in state
            or "  change_impact_driven_ci_governance_amendment_correction_1_record: docs/architecture/governance/change-impact-driven-ci-governance-amendment-1-correction-1.md\n" not in state):
        raise AssertionError("current state does not index the adopted amendment and correction")
    if (correction.count("CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1_FINAL_REVIEW") != 2
            or "BASE=4cc61569f284a53efff0bb8462a4f8a416f04ed5" not in correction):
        raise AssertionError("historical append-forward correction evidence drifted")


def expect_red(
    name: str, standard: str, foundation: str, standalone_semgrep_exists: bool = False
) -> None:
    try:
        assert_workflow_contract(standard, foundation, standalone_semgrep_exists)
    except AssertionError:
        return
    raise AssertionError(f"RED mutation passed: {name}")


def expect_governance_red(name: str, state: str) -> None:
    try:
        assert_phase18_post_integration_state(state)
    except AssertionError:
        return
    raise AssertionError(f"RED mutation passed: {name}")


def main() -> None:
    for case in CASES:
        assert_case(*case)

    empty = classifier.Classification.from_paths([], "matrix")
    if set(empty.categories) != {"unknown"} or not empty.policy()["full_ci"] or empty.policy()["runtime_image_publish"]:
        raise AssertionError("empty change set did not fail closed without image publication")
    mixed = classifier.Classification.from_paths(
        ["docs/architecture/governance/example.md", "render-module/src/test/java/ExampleTest.java"],
        "matrix",
    )
    if mixed.policy()["runtime_image_publish"] or not mixed.policy()["backend_ci"]:
        raise AssertionError("governance/test-only change could publish a runtime image")
    formal_only = classifier.Classification.from_paths(
        ["formal/lean/Faof2Graph.lean", "formal/coq/Faof2Graph.v"], "matrix"
    )
    if (not formal_only.policy()["formal_verification"]
            or formal_only.policy()["backend_ci"]
            or formal_only.policy()["runtime_image_publish"]):
        raise AssertionError("formal-only change did not remain formal-only and non-publishing")
    formal_runtime_mixed = classifier.Classification.from_paths(
        ["formal/lean/Faof2Graph.lean", "render-module/src/main/java/Example.java"], "matrix"
    )
    if (not formal_runtime_mixed.policy()["formal_verification"]
            or not formal_runtime_mixed.policy()["backend_ci"]
            or not formal_runtime_mixed.policy()["runtime_image_publish"]):
        raise AssertionError("actual mixed formal/runtime diff lost runtime effects")

    standard = STANDARD_CI.read_text()
    foundation = FOUNDATION_CI.read_text()
    assert_workflow_contract(standard, foundation, SEMGREP_CI.exists())
    assert_governance_contract()
    mutations = (
        ("old-path-authority", standard.replace("pull_request:\n", "pull_request:\n    paths-ignore:\n      - 'docs/**'\n", 1), foundation),
        ("backend-unconditional", standard.replace("if: needs.change-impact.outputs.backend_ci == 'true'", "if: always()", 1), foundation),
        ("image-unconditional", standard.replace("needs.change-impact.outputs.runtime_image_publish == 'true'", "github.ref == 'refs/heads/main'", 1), foundation),
        ("shallow-checkout", standard.replace("fetch-depth: 0", "fetch-depth: 1", 1), foundation),
        ("foundation-coupled", standard, foundation.replace("if: needs.change-impact.outputs.backend_ci == 'true'", "if: needs.change-impact.outputs.architecture_drift == 'true'", 1)),
        ("semgrep-unconditional", standard.replace("if: needs.change-impact.outputs.semgrep_validation == 'true'", "if: always()", 1), foundation),
        ("formal-unconditional", standard.replace("if: needs.change-impact.outputs.formal_verification == 'true'", "if: always()", 1), foundation),
        ("semgrep-policy-need-removed", standard.replace(", semgrep]", "]", 1), foundation),
        ("semgrep-policy-enforcement-removed", standard.replace('          require_result "$SEMGREP_REQUIRED" "$SEMGREP_RESULT" semgrep\n', "", 1), foundation),
        ("conditional-skip-contract-weakened", standard.replace('test "$result" = "skipped"', 'test "$result" = "success"', 1), foundation),
        ("standard-runtime-setup-restored", standard.replace("      - name: Run PFIRR1 remediation gates\n", "      - run: bash scripts/ci/setup-test-runtime.sh\n\n      - name: Run PFIRR1 remediation gates\n", 1), foundation),
        ("standard-compile-removed", standard.replace("      - name: Compile production and test sources\n        run: ./gradlew --no-daemon compileJava compileTestJava\n\n", "", 1), foundation),
        ("foundation-full-test-restored", standard, foundation.replace("      - name: Build boot jar smoke check\n", "      - run: ./gradlew --no-daemon test\n\n      - name: Build boot jar smoke check\n", 1)),
        ("foundation-jooq-proof-removed", standard, foundation.replace("      - name: Prove jOOQ authority verification is fail-closed\n        run: bash scripts/verify-pfirr1-jooq-authority-fail-closed.sh\n\n", "", 1)),
        ("standalone-semgrep-restored", standard, foundation, True),
    )
    for mutation in mutations:
        expect_red(*mutation)

    state = CURRENT_STATE.read_text()
    governance_mutations = (
        (
            "old-phase18-in-progress-state",
            state.replace("  phase_18: CLOSED\n", "  phase_18: IN_PROGRESS\n", 1).replace(
                "  phase_18_faof_2_bounded_implementation: CLOSED\n",
                "  phase_18_faof_2_bounded_implementation: IN_PROGRESS\n",
                1,
            ),
        ),
        (
            "wrong-phase19-authorization-gate",
            state.replace(PHASE19_AUTHORIZATION_GATE, "ARBITRARY_AGREED_GATE"),
        ),
        (
            "pending-phase18-integration-state",
            state.replace(
                "  phase_18_canonical_main_integration: COMPLETED_FAST_FORWARD_ONLY\n",
                "  phase_18_canonical_main_integration: AUTHORIZED_PENDING_FAST_FORWARD_ONLY\n",
            ),
        ),
        (
            "phase19-not-started",
            state.replace("  phase_19_started: true\n", "  phase_19_started: false\n"),
        ),
        (
            "phase19-authorization-drift",
            state.replace(
                "  phase_19_implementation_authorization: AUTHORIZED\n",
                "  phase_19_implementation_authorization: NOT_AUTHORIZED\n",
            ),
        ),
        (
            "integrated-main-identity-drift",
            state.replace(PHASE18_INTEGRATED_SHA, "2" * 40).replace(
                PHASE18_INTEGRATED_TREE, "3" * 40
            ),
        ),
        (
            "wrong-phase18-accepted-identity",
            state.replace(PHASE18_ACCEPTED_SHA, "0" * 40).replace(
                PHASE18_ACCEPTED_TREE, "1" * 40
            ),
        ),
    )
    for mutation in governance_mutations:
        expect_governance_red(*mutation)

    original_classify_path = classifier.classify_path
    classifier.classify_path = lambda path: (("unknown",) if path == ".dockerignore"
                                               else original_classify_path(path))
    try:
        try:
            assert_case("root-dockerignore-rule-removal", ".dockerignore", {"container"},
                        {"backend_ci", "runtime_image_publish"})
        except AssertionError:
            pass
        else:
            raise AssertionError("RED mutation passed: root-dockerignore-container-rule-removal")
    finally:
        classifier.classify_path = original_classify_path

    print(
        f"CHANGE_IMPACT_CLASSIFIER_RED_MATRIX=PASS cases={len(CASES) + 4} "
        f"workflow_mutations={len(mutations)} governance_mutations={len(governance_mutations)} "
        "classifier_mutations=1"
    )


if __name__ == "__main__":
    main()
