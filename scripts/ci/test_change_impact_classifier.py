#!/usr/bin/env python3
"""Executable GREEN/RED policy matrix for the change-impact classifier."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLASSIFIER_PATH = ROOT / "scripts/ci/change_impact_classifier.py"
STANDARD_CI = ROOT / ".github/workflows/ci.yml"
FOUNDATION_CI = ROOT / ".github/workflows/architecture-drift.yml"
SEMGREP_CI = ROOT / ".github/workflows/semgrep-architecture.yml"
AMENDMENT = ROOT / "docs/architecture/governance/change-impact-driven-ci-governance-amendment-1.md"
CORRECTION = ROOT / "docs/architecture/governance/change-impact-driven-ci-governance-amendment-1-correction-1.md"
REGISTRY = ROOT / "docs/architecture/governance/project-state/architecture-registry.yaml"
CURRENT_STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"

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
    ("workflow", ".github/workflows/ci.yml", {"workflow"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation"}),
    ("ci-infrastructure", "scripts/ci/setup-test-runtime.sh", {"ci_infrastructure"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation"}),
    ("unknown", "unowned/new-surface.xyz", {"unknown"}, {"full_ci", "backend_ci", "frontend_ci", "architecture_drift", "gitops_validation", "semgrep_validation"}),
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
    if "policy-summary:" not in standard or "if: always()" not in standard:
        raise AssertionError("Standard CI policy summary is not unconditional")
    image_gate = "needs.change-impact.outputs.runtime_image_publish == 'true'"
    if image_gate not in standard:
        raise AssertionError("runtime image publication is not classifier-gated")
    if "if: needs.change-impact.outputs.architecture_drift == 'true'" not in foundation:
        raise AssertionError("Foundation architecture drift is not independently conditional")
    if "if: needs.change-impact.outputs.backend_ci == 'true'" not in foundation:
        raise AssertionError("Foundation full backend is not independently conditional")
    if standard.count("\n  semgrep:\n") != 1:
        raise AssertionError("Standard CI does not own exactly one Semgrep job")
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
        "needs: [change-impact, backend, frontend, gitops-validation, semgrep]",
        "SEMGREP_REQUIRED: ${{ needs.change-impact.outputs.semgrep_validation }}",
        "SEMGREP_RESULT: ${{ needs.semgrep.result }}",
        'echo "- semgrep: required=$SEMGREP_REQUIRED result=$SEMGREP_RESULT"',
        'test "$result" = "success"',
        'test "$result" = "skipped"',
        'require_result "$SEMGREP_REQUIRED" "$SEMGREP_RESULT" semgrep',
    )
    if any(item not in standard for item in required_semgrep_policy_contract):
        raise AssertionError("Standard CI policy summary does not report and enforce Semgrep")
    for name, workflow in (("standard", standard), ("foundation", foundation)):
        checkout_count = workflow.count("uses: actions/checkout@v4")
        if workflow.count("fetch-depth: 0") < checkout_count:
            raise AssertionError(f"{name}: classifier-dependent checkout has shallow history")
        if workflow.count("persist-credentials: false") < checkout_count:
            raise AssertionError(f"{name}: classifier-dependent checkout persists credentials")


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
    gate = "CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1_FINAL_REVIEW"
    obsolete = "CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_FINAL_REVIEW"
    if state.count(gate) != 2 or obsolete in state:
        raise AssertionError("current state does not freeze the exact Correction 1 gate")
    required_state = (
        "  phase_17: CLOSED\n",
        "  phase_18_started: false\n",
        "    phase: 18\n",
        "    - FAOF-2\n",
    )
    if any(item not in state for item in required_state):
        raise AssertionError("Phase 17/18/FAOF-2 frozen lifecycle state drifted")
    if ("    change_impact_driven_ci_governance: ADOPTED\n" not in state
            or "  change_impact_driven_ci_governance_amendment: docs/architecture/governance/change-impact-driven-ci-governance-amendment-1.md\n" not in state
            or "  change_impact_driven_ci_governance_amendment_correction_1_record: docs/architecture/governance/change-impact-driven-ci-governance-amendment-1-correction-1.md\n" not in state):
        raise AssertionError("current state does not index the adopted amendment and correction")
    if correction.count(gate) != 2 or "BASE=4cc61569f284a53efff0bb8462a4f8a416f04ed5" not in correction:
        raise AssertionError("append-forward correction does not freeze its base and exact review gate")


def expect_red(
    name: str, standard: str, foundation: str, standalone_semgrep_exists: bool = False
) -> None:
    try:
        assert_workflow_contract(standard, foundation, standalone_semgrep_exists)
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
        ("semgrep-policy-need-removed", standard.replace(", semgrep]", "]", 1), foundation),
        ("semgrep-policy-enforcement-removed", standard.replace('          require_result "$SEMGREP_REQUIRED" "$SEMGREP_RESULT" semgrep\n', "", 1), foundation),
        ("conditional-skip-contract-weakened", standard.replace('test "$result" = "skipped"', 'test "$result" = "success"', 1), foundation),
        ("standalone-semgrep-restored", standard, foundation, True),
    )
    for mutation in mutations:
        expect_red(*mutation)

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
        f"CHANGE_IMPACT_CLASSIFIER_RED_MATRIX=PASS cases={len(CASES) + 2} "
        f"workflow_mutations={len(mutations)} classifier_mutations=1"
    )


if __name__ == "__main__":
    main()
