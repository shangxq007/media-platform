# Change-Impact-Driven CI Governance — Amendment 1 Correction 1

TASK_ID=CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1
MODE=APPEND_FORWARD_GOVERNANCE_AND_CI_ONLY
STATUS=IMPLEMENTED_PENDING_INDEPENDENT_FINAL_REVIEW
BASE=4cc61569f284a53efff0bb8462a4f8a416f04ed5
PARENT_RECORD=docs/architecture/governance/change-impact-driven-ci-governance-amendment-1.md
PHASE_17=CLOSED
PHASE_18_STARTED=false
NEXT_ROADMAP_EXECUTION=ROADMAP_22_PHASE_18_FAOF_2_FORMAL_ALGORITHM_VALIDATION
LOCAL_VALIDATION=PASS

## 1. Scope and precedence

This append-forward correction narrows the implementation of Amendment 1
without rewriting its adopted principles. It corrects Standard CI ownership,
the container taxonomy, executable contracts, and the exact independent-review
gate. It does not reopen Phase 17, start Phase 18, alter production or test
source, change migrations, or modify runtime container inputs.

Where the parent implementation leaves Semgrep execution in a separate
classifier-dependent workflow, this correction is the current operational
authority: canonical Semgrep execution is a classifier consumer inside the
active Standard CI workflow.

## 2. C1 corrections

1. `SEMGREP_EXECUTION_HAS_ONE_STANDARD_CI_AUTHORITY_C1` —
   `.github/workflows/ci.yml` owns the classifier-gated `semgrep` job. The job
   needs `change-impact`, runs only when `semgrep_validation` is `true`, checks
   out complete history without persisted credentials, and uses the existing
   `semgrep/semgrep-action@v1` action and
   `.semgrep/media-platform-architecture.yml` rules.
2. `STANDALONE_SEMGREP_WORKFLOW_IS_CLEAN_FORWARDED_C1` — the former
   `.github/workflows/semgrep-architecture.yml` parallel execution surface is
   deleted. The workflow contract requires Standard CI ownership and rejects
   restoration of the standalone workflow.
3. `STANDARD_CI_POLICY_SUMMARY_ENFORCES_SEMGREP_C1` — `policy-summary` needs
   the Semgrep job, reports its result, and enforces the exact conditional-job
   contract: required `true` means `success`; required `false` means `skipped`.
4. `ROOT_DOCKERIGNORE_IS_CONTAINER_IMPACT_C1` — the root `.dockerignore` is
   classified as `container`, selecting `backend_ci=true` and
   `runtime_image_publish=true`. The executable RED matrix rejects removal of
   this rule.

## 3. Frozen lifecycle and gate correction

The accepted Phase 17 post-integration state remains `CLOSED`. Phase 18 remains
not started, and the next execution remains Roadmap 22 Phase 18, FAOF-2 Formal
Algorithm Validation. Both current-state gate fields and the Phase 17 ledger
guard require exactly:

`CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1_FINAL_REVIEW`

The Phase 17 ledger RED matrix proves that the old Amendment 1 final-review
gate and an arbitrary mutually matching gate fail, while the exact Correction
1 gate passes with the otherwise frozen post-integration state.

## 4. Validation contract

The bounded candidate must pass the classifier GREEN/RED matrix, explicit root
`.dockerignore` classification, the Phase 17 ledger guard and its RED matrix,
the architecture drift gate, and local YAML parsing for every changed YAML
workflow/state document. Validation must also prove that no production source,
test source, migration, Dockerfile, or `.dockerignore` content changed.

FINAL_REVIEW_GATE=CHATGPT_CHANGE_IMPACT_DRIVEN_CI_GOVERNANCE_AMENDMENT_1_CORRECTION_1_FINAL_REVIEW

## 5. Local validation evidence

- `python3 scripts/ci/test_change_impact_classifier.py` — PASS, 15 cases, 10
  workflow mutations, and 1 classifier mutation.
- `python3 scripts/ci/change_impact_classifier.py --path .dockerignore --json`
  — PASS, `container`, `backend_ci=true`, `runtime_image_publish=true`.
- Phase 17 ledger guard and RED matrix — PASS, 131 rows and 24 mutations,
  including the exact gate pass and rejection of the old Amendment 1 and
  arbitrary matching gates.
- `bash scripts/check-architecture-drift.sh` — PASS.
- local YAML parsing for Standard CI, Foundation Verification, frozen Phase 17
  conformance, current state, and roadmap tracks — PASS.
- `git diff --check` and bounded prohibited-path audit — PASS.
