# Change-Impact-Driven CI Governance — Amendment 1

TASK_ID=CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1
MODE=APPEND_FORWARD_GOVERNANCE_AND_CI_ONLY
STATUS=ADOPTED_REPOSITORY_PERSISTED
IMPLEMENTATION=IMPLEMENTED_PENDING_INDEPENDENT_FINAL_REVIEW
BASE=e01ceeaa709f864c8dfe6ada6975142ce2cd0a6f
COMPETING_CI_AUTHORITY=NO
PHASE_17=CLOSED
PHASE_18_STARTED=false
NEXT_ROADMAP_EXECUTION=ROADMAP_22_PHASE_18_FAOF_2

## 1. Scope and precedence

This amendment replaces path filters and unconditional broad workflow jobs as
change-selection authority with one platform-owned classifier. It changes CI,
repository validation scripts, and governance state only. It does not reopen
Phase 17, start Phase 18, change production/runtime code, alter application
semantic tests or migrations, or implement Provider, runtime, or FAOF-2 work.

`scripts/ci/change_impact_classifier.py` is the sole path-to-policy authority.
Workflows may consume its outputs but must not maintain competing path
taxonomies. Event triggers remain scheduling mechanics, not a second impact
classifier. The existing Phase 17 conformance workflow remains a frozen,
branch- and runner-specific historical conformance lane rather than general CI
authority.

## 2. Adopted policy principles

The following eight stable principles are adopted and indexed in the
architecture registry.

1. `CI_CHANGE_IMPACT_CLASSIFICATION_IS_PLATFORM_OWNED_V1` — repository-owned
   executable code, not third-party path-filter configuration, owns impact
   classification and policy outputs.
2. `CHANGE_IMPACT_TAXONOMY_IS_EXHAUSTIVE_AND_UNKNOWN_FAILS_CLOSED_V1` — the
   taxonomy is governance, docs, frontend, backend test, backend runtime,
   build graph, container, GitOps, Semgrep, workflow, CI infrastructure, and
   unknown; an empty, invalid, unresolvable, or unmatched change becomes
   unknown and selects full CI.
3. `BACKEND_AND_FRONTEND_CI_ARE_INDEPENDENTLY_IMPACT_GATED_V1` — Standard CI
   and Foundation select backend and frontend work independently; architecture
   drift is distinct from Foundation full-backend verification.
4. `CI_POLICY_SUMMARY_IS_UNCONDITIONAL_V1` — Standard CI always emits and
   enforces a stable policy-summary result, including when selected jobs are
   skipped or fail.
5. `RUNTIME_IMAGE_PUBLICATION_REQUIRES_RUNTIME_CONTAINER_OR_BUILD_INPUT_IMPACT_V1`
   — a main-branch push may publish runtime images only when at least one
   classified path is backend runtime, container, or backend build-graph input.
6. `NON_RUNTIME_CHANGES_MUST_NOT_PUBLISH_RUNTIME_IMAGES_V1` — governance,
   documentation, backend-test-only, frontend-only, GitOps, Semgrep, workflow,
   CI-infrastructure, and unknown-only changes cannot publish runtime images.
7. `GITOPS_SEMGREP_AND_ARCHITECTURE_VALIDATION_ARE_TARGETED_V1` — GitOps,
   Semgrep, architecture drift, and Foundation backend verification are
   explicit classifier outputs; unknown, workflow, and CI-infrastructure
   impact selects every validation lane.
8. `CLASSIFIER_DEPENDENT_CHECKOUTS_USE_COMPLETE_CREDENTIAL_FREE_HISTORY_V1`
   — every checkout in a classifier-dependent workflow uses `fetch-depth: 0`
   and `persist-credentials: false`, preserving Git-qualified governance
   evidence without retaining checkout credentials.

## 3. Executable decision matrix

The authoritative classifier can assign more than one category when a path
has more than one impact (for example, `frontend/Dockerfile` is frontend and
container). The policy union applies.

| Impact | Backend | Frontend | Architecture | GitOps | Semgrep | Runtime image |
|---|---:|---:|---:|---:|---:|---:|
| governance | no | no | yes | no | no | no |
| docs | no | no | no | no | no | no |
| frontend | no | yes | no | no | no | no |
| backend test | yes | no | no | no | no | no |
| backend runtime | yes | no | yes | no | yes | yes |
| build graph | yes | no | yes | no | yes | yes |
| container | yes | no | no | no | no | yes |
| GitOps | no | no | no | yes | no | no |
| Semgrep | no | no | no | no | yes | no |
| workflow / CI infrastructure / unknown | yes | yes | yes | yes | yes | no |

`scripts/ci/test_change_impact_classifier.py` is the executable GREEN/RED
matrix. It proves every taxonomy row and rejects mutations that restore an
older path authority, make backend or Semgrep unconditional, couple Foundation
backend to architecture drift, allow shallow history, or remove the exact
runtime-image decision gate. The matrix is run by the classifier job and by
the architecture-drift guard.

## 4. Workflow application

Standard CI has one classifier job, independently conditional backend and
frontend jobs, targeted GitOps validation using the existing static readiness
and egress mechanics, an unconditional policy summary, and classifier-gated
main image publication. Governance, test, workflow, classifier, and other CI
changes no longer cause an image publication merely because they reached
`main`.

Foundation Verification has separate classifier consumers for architecture
drift and full backend verification. Semgrep has no independent workflow path
list; its scan is selected by the classifier for backend runtime, build graph,
Semgrep policy, and full-CI impacts.

## 5. Frozen lifecycle state and final gate

The Phase 17 post-integration ledger remains closed against the accepted
canonical source and exact deletion evidence. Its accepted post-integration
state now requires both current-state gate fields to equal:

`CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_FINAL_REVIEW`

The RED matrix proves that the obsolete Correction-1 gate fails even when both
gate fields match, an arbitrary mutually matching gate fails, and the exact
amendment gate passes with the otherwise frozen Phase 17 state. Phase 18 stays
`false`; the next roadmap execution after this governance gate remains Roadmap
#22 Phase 18 FAOF-2 Formal Algorithm Validation.
