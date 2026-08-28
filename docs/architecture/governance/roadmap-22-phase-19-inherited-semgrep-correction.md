# Roadmap 22 Phase 19 Inherited Semgrep Gate Correction

## Task identity and append-forward intent

- Task: `ROADMAP_22_PHASE_19_INHERITED_SEMGREP_GATE_CORRECTION`
- Owned worktree: `/home/user/Documents/workspace/projects/.worktrees/roadmap22-phase19-inherited-semgrep-correction`
- Owned branch: `agent/roadmap22-phase19-inherited-semgrep-correction`
- Base `HEAD`: `b626f2eb4fd462ada1db018720e8a06bbed61883`
- Base parent: `c058e187cfbb2fdb8037aca21ae333a7df27a4bb`
- Intent: append this bounded three-file correction forward from the exact base.
- Candidate state: `SEMGREP_CORRECTION_CANDIDATE_CREATED=YES_BY_THIS_COMMIT`.
- Candidate SHA source: `DERIVED_FROM_GIT_COMMIT_CONTAINING_THIS_RECORD`.

The repository-root `AGENTS.md` is the only applicable instruction file. Its
scope covers the whole repository. It conflicts with neither the Owner task nor
the explicit no-Git-mutation constraint. Initial status was clean. The shared
stash list contained one pre-existing entry for another task; it was not read,
changed, applied, or dropped.

## Bounded correction

The two inherited invalid Java rule definitions were replaced in place; the
invalid definitions were not retained, duplicated, or disabled.

- `arch-no-remotion-production` now uses valid Java patterns bounded by a
  syntactic `ProviderStatus.PRODUCTION` `if` block and concrete Remotion
  construction, authority-call, or dispatch-argument forms. Production-main
  paths exclude tests and committed fixture noise.
- `arch-no-artifact-dag-runtime` now detects concrete construction or method
  activation on explicitly ArtifactDAG-named runtime authorities in
  production-main paths. It does not treat `ArtifactDAGImpact` values,
  declarations, documentation, comments, or tests as runtime activation.
- `arch-no-provider-key-exposure` remains `ERROR`. It requires an exact bounded
  sensitive response key and credential-like value material. Bare scheme
  metadata is not credential material.
- The existing `platform-app/build.gradle*` include was made explicitly
  repository-root anchored without changing its meaning.

`PROVIDER_KEY_FINDING_CLASSIFICATION=FALSE_POSITIVE`

`TOKEN_TYPE_BEARER=SCHEME_METADATA_NOT_CREDENTIAL`

The adjacent development `accessToken` response is an intentional development
authentication response, not a provider/API-key exposure. The controller
response shape was not changed.

## Strict TDD evidence

The contract test was the only changed file when RED was captured. Command:

```text
python3 scripts/ci/test_semgrep_architecture_rules.py
```

RED result: contract exit `1`; nested Semgrep validation exit `2`; Semgrep
reported `Pattern parse error in rule arch-no-remotion-production` for the
legacy Java fragment containing `Remotion...`. This proves the test observed the
inherited invalid configuration before any rule correction.

After the minimal configuration correction, the same command returned exit
`0` with the exact matrix token:

```text
SEMGREP_ARCHITECTURE_CONTRACT_MATRIX=PASS VALIDATE=1/1 MALFORMED_REJECTIONS=2/2 POSITIVE_CASES=4/4 POSITIVE_FINDINGS=4 NEGATIVE_CASES=3/3 NEGATIVE_FINDINGS=0 UNEXPECTED_FINDINGS=0
```

The two malformed-rejection cases independently mutate the corrected Remotion
and Artifact DAG rules back to representative invalid Java ellipsis patterns;
each must produce a Semgrep pattern-validation failure. The positive fixtures
cover Remotion production construction, Artifact DAG runtime activation, a
synthetic provider/API credential-like literal, and a synthetic `Bearer
<credential>` response. The negatives cover Remotion outside production and in
POC/STUB blocks, harmless ArtifactDAG impact/test forms, and
`tokenType="Bearer"` scheme metadata.

## Full validation evidence

All commands ran from the owned worktree against the final bounded diff.

| Command | Exit | Exact result |
| --- | ---: | --- |
| `python3 scripts/ci/test_semgrep_architecture_rules.py` | 0 | matrix PASS; validate `1/1`; malformed rejections `2/2`; positive cases/findings `4/4`; negative findings `0`; unexpected findings `0` |
| `uvx semgrep --validate --config .semgrep/media-platform-architecture.yml` | 0 | `0` configuration errors; `7` rules |
| `uvx semgrep --config .semgrep/media-platform-architecture.yml .` | 0 | `7` rules; `3,722` targets; parsed lines approximately `100.0%`; parse errors `0`; findings `0` (`0` blocking); no unanchored-path warning |
| `python3 scripts/ci/test_change_impact_classifier.py` | 0 | `CHANGE_IMPACT_CLASSIFIER_RED_MATRIX=PASS cases=19 workflow_mutations=11 governance_mutations=7 classifier_mutations=1` |
| `bash scripts/check-architecture-drift.sh` | 0 | `Checks: 42`, `Failed: 0`, final `All architecture drift checks passed` |
| `./gradlew --no-daemon --max-workers=1 test --rerun-tasks` | 0 | 8,138 tests; 8,106 passed; failures `0`; errors `0`; skipped `32`; 200 executed tasks; 22m48s |
| `./gradlew --no-daemon --max-workers=1 pfirr1RemediationCheck :platform-app:bootJar -x test --rerun-tasks` | 0 | PFIRR1 remediation and bootJar PASS; 105 executed tasks |
| `bash scripts/verify-pfirr1-jooq-authority-fail-closed.sh` | 0 | all three authority-removal negative proofs PASS; non-mutating |
| frontend `npm ci`, lint, Vitest, build | 0 | lint errors `0` (56 inherited warnings); Vitest `3/3`; Vite build PASS; generated residue restored to zero |
| `scripts/formal/validate-faof2.sh` | 0 | Lean 4.19.0, Coq 8.20.1, proof-hole/witness/RED matrix PASS |
| staging and production GitOps validation | 0 | all critical readiness and egress checks PASS; inherited warnings only |
| `git diff --check` | 0 | no output |

Semgrep full-scan arithmetic: targets `3,722`; findings `0`; blocking
findings `0`; parse errors `0`.

## Scope and lifecycle assertions

The final scope is exactly:

1. `.semgrep/media-platform-architecture.yml` — Semgrep configuration only.
2. `scripts/ci/test_semgrep_architecture_rules.py` — new stdlib executable
   contract test.
3. `docs/architecture/governance/roadmap-22-phase-19-inherited-semgrep-correction.md`
   — this governance evidence record.

No FFmpeg, plugin, distribution, classifier, workflow, frontend, test/production
source, or other application/runtime file changed. In particular,
`DevAuthController` was not changed.

`C3_PRODUCTION_SEMANTICS_CHANGED_BY_SEMGREP_CORRECTION=NO`

`DEV_AUTH_CONTROLLER_CHANGED=NO`

`MODULITH_TYPED_ALLOWANCE_FOLLOWUP=DEFERRED_NON_BLOCKING`

C2 remains pending. Phase 19 remains open. The prohibited scopes were not
started. This correction commit is the only authorized Git mutation in this
worktree. No amend, rebase, squash, force push, canonical-main integration,
publication, deployment, or unrelated remote mutation is authorized.
