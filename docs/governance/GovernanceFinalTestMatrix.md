# Document governance and guard simplification — final test matrix

**Task:** `MEDIA_PLATFORM_DOCUMENT_GOVERNANCE_AND_GUARD_SIMPLIFICATION_R1`
**Base main:** `6f995ce73756822e4c781dbe8a76060abc20be45` (tree `df9053ce06c821e8a74a49ae7fa22c6ed6b50efd`)
**Code candidate:** `77bcf80a36f5b206a4089841b9f9b105cc45f005` (tree `373990233071ad13c8671694ade9dfb88fa069e5`)
**Final candidate:** the commit containing this file; resolve with `git log -1`. Every manifest records
`identity.commit` / `identity.tree`; read the newest manifest under `…-evidence/verify/` instead of
trusting this table for a different commit.

Environment: JDK 25.0.5, Gradle 9.1.0 wrapper, Linux, rootless Podman present but **no protected PG18
binding**. No production runtime, deployment, schema or API change.

## Gate results

| Gate | Result | Commit / tree | Command | Exit | Evidence |
|---|---|---|---|---|---|
| DG006_RESOLUTION | PASS | `77bcf80a` / `3739902` | `python3 scripts/dev/verify docs --base main` | 0 | DG-006 18/18 checked, 0 violations; `…/verify/20261115T034451Z/document-governance.stdout` |
| DG011_RESOLUTION | PASS | `77bcf80a` / `3739902` | same | 0 | DG-011 `introduced=0`, `head_broken=49`, `base_broken=49`, `accepted_debt=126` |
| DOCUMENT_GOVERNANCE | PASS | `77bcf80a` / `3739902` | `scripts/check-document-governance.sh --head HEAD --base main` | 0 | 16/16 guards; `20261115T034451Z/document-governance.json` |
| INSTRUCTION_GOVERNANCE | PASS | `77bcf80a` / `3739902` | `python3 scripts/governance/check-instruction-governance.py` | 0 | 4 instruction files inventoried |
| INSTRUCTION_SELFTEST | PASS | `77bcf80a` / `3739902` | `python3 scripts/governance/test_check_instruction_governance.py` | 0 | 7/7 mutation cases rejected |
| ARCHITECTURE_GUARDS | PASS | `77bcf80a` / `3739902` | `bash scripts/check-architecture-drift.sh` (via `verify guards`) | 0 | `ARCHITECTURE_DRIFT: PASS (219 checks)`, 0 failures, 30.5 s |
| GUARD_NEGATIVE_TESTS | PASS | `77bcf80a` / `3739902` | document-governance self-test + instruction self-test | 0 | 10/10 + 7/7 cases |
| VERIFY_L1 | PASS | `77bcf80a` / `3739902` | `python3 scripts/dev/verify preflight --level l1` | 0 | `PREFLIGHT PASS`, blockers `[]` |
| VERIFY_L2 | BLOCKED | — | `python3 scripts/dev/verify l2 --binding …` | 1 | fail-closed `L2 BLOCKED: protected PG18 launcher inputs missing`; no controller binding available |
| VERIFY_GUARDS | PASS | `77bcf80a` / `3739902` | `python3 scripts/dev/verify guards` | 0 | 6/6 results exit 0; `20261115T034414Z/manifest.json` |
| ARTIFACT_CONTROLLED_TESTS | BLOCKED | — | `./gradlew :artifact-module:test` (prior evidence) | 1 | 170 tests: 159 pass, 11 `CONTROLLED_LAUNCH_REQUIRED`, 0 skip/error; unchanged this round |
| PRODUCT_RUNTIME_UNCHANGED | PASS | `77bcf80a` / `3739902` | `git diff --name-only dad62373..HEAD -- '*.java'` | 0 | empty; no Flyway, controller, DTO or execution change |

`verify guards` manifest items (commit `77bcf80a`, tree `3739902…`, dirty `[]`): architecture-drift 0
(30.5 s), jooq-parity 0 (5.7 s), oidc-bundle 0, instruction-governance 0, instruction-governance-self
test 0, document-governance-self-test 0 → `results_pass: true`.

## Document-governance controlled cases

`scripts/document-governance/test_document_governance_guard.py` builds a temporary Git repository:

| Case | Expected | Result |
|---|---|---|
| DG-006 unchanged protected body | pass | pass |
| DG-006 unchanged body on a later commit | pass | pass |
| DG-006 changed protected body | fail `PROTECTED_BASELINE_CHANGE` | fail as expected |
| DG-006 invalid head | fail `INVALID_HEAD_REF` | fail as expected |
| DG-011 same commit | pass (0 introduced) | pass |
| DG-011 valid new link | pass | pass |
| DG-011 genuinely new broken link | fail `INTRODUCED_BROKEN_LINK` | fail as expected |
| DG-011 same broken commit | pass | pass |
| DG-011 unrelated change | pass (no false positive) | pass |
| DG-011 invalid ref | fail `INVALID_DIFF_INPUT` | fail as expected |

Real-repository contrast: `--head 6f995ce7 --base e7cea1cb~1` reports the one introduced link that
was actually broken; `--head 6f995ce7 --base 6f995ce7` reports zero.

## Scope check

- `git diff --check`: no whitespace errors.
- `git status --short` before commit: only the intended governance files.
- Changed paths (`dad62373..77bcf80a`): two document-governance checkers, one new document-governance
  self-test, `scripts/check-architecture-drift.sh`, `scripts/dev/verify`, the protected-document
  baseline record, one source-of-truth link repair, guard manifest, `docs/dev/verify.md` and
  `GovernanceResolution.md`.
- Not changed: production Java, Flyway V1, controller/DTO/OpenAPI surfaces, persisted
  `first-render-release:` identity, First Render execution path, CI workflow topology, host settings.

## Blocked items (unchanged facts)

| Item | Status | Reason |
|---|---|---|
| 11 `CONTROLLED_LAUNCH_REQUIRED` Artifact tests | BLOCKED | no protected PG18 controller binding; launcher fails closed by design |
| L3 product E2E | NOT_RUN | governance/tooling-only change; not required by the task |

## How to reproduce

```sh
python3 scripts/dev/verify preflight --level l1
python3 scripts/dev/verify guards
python3 scripts/dev/verify docs --base main
bash scripts/check-architecture-drift.sh            # verbose, 219 checks
python3 scripts/document-governance/test_document_governance_guard.py
```
