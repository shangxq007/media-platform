# Engineering rules and guards consolidation — test matrix

**Task:** `MEDIA_PLATFORM_ENGINEERING_RULES_AND_GUARDS_CONSOLIDATION_R1`
**Base main:** `6f995ce73756822e4c781dbe8a76060abc20be45` (tree `df9053ce06c821e8a74a49ae7fa22c6ed6b50efd`)
**Code candidate:** `0cf42bdd97161ae12838ab0a80efe85adb8f5dbf` (tree `f5cfb3881d2f23812b0eba6ce5264c42f3461a88`)
**Final candidate:** the commit containing this file (documentation only on top of the code candidate);
resolve with `git log -1`. Reviewers should re-run `python3 scripts/dev/verify guards` on the exact
reviewed commit — the manifest records commit/tree.

Environment: JDK 25.0.5 (OpenJDK, Suse build), Gradle 9.1.0 wrapper, Linux, rootless Podman available
but no protected container binding. No product runtime, deployment or production data touched.

## Executed checks

| # | Level | Command | Exit | Result | Evidence |
|---|---|---|---|---|---|
| 1 | L1 | `python3 scripts/dev/verify preflight --level l1 --json` | 0 | PASS (tools, identity, evidence writable) | stdout captured in session; blockers `[]` |
| 2 | L1 | `python3 scripts/governance/check-instruction-governance.py` | 0 | PASS, 4 instruction files inventoried | `verify/20261115T005650Z/instruction-governance.stdout` |
| 3 | L1 | `python3 scripts/governance/test_check_instruction_governance.py` | 0 | PASS, 7/7 mutation cases rejected | `verify/20261115T005650Z/instruction-governance-selftest.stdout` |
| 4 | L1 | `python3 scripts/dev/verify guards` | 0 | PASS, 5/5 guards green on commit `0cf42bdd` | `…-evidence/verify/20261115T005650Z/manifest.json` |
| 5 | L1 | `./gradlew :artifact-module:test --tests '*JooqArtifactCommitServiceProvenanceErrorContextTest*'` | 0 | PASS, 4/4 (was 4 failures) | `artifact-module/build/test-results/test/` |
| 6 | L1 | `./gradlew :artifact-module:test` (full module) | 1 | 170 tests: 159 passed, 11 failed, 0 skipped, 0 errors — all 11 `CONTROLLED_LAUNCH_REQUIRED` | `artifact-module/build/test-results/test/TEST-*.xml` |
| 7 | L1 | `./gradlew :platform-app:compileTestJava :media-execution-plan-module:compileJava` | 0 | PASS after bean rename and message change | session log |
| 8 | L1 | `bash scripts/check-architecture-drift.sh` | 0 | PASS on committed candidate (H7 bound-tree check requires a clean tree) | `verify/20261115T005650Z/architecture-drift.stdout` |
| 9 | L2 gate | `python3 scripts/dev/verify l2 --binding … --password-file … --classpath-file …` (dummy paths) | 1 | Correct fail-closed `L2 BLOCKED: protected PG18 launcher inputs missing` | session log |
| 10 | L3 gate | `python3 scripts/dev/verify preflight --level l3 --json` | 1 | BLOCKED: `worker ffmpeg lacks libx264`, `FFMPEG_WORKER_FONT` unset, PG18/Temporal/OIDC ports closed | session log |
| 11 | Governance | `bash scripts/check-document-governance.sh --head HEAD --base 6f995ce7` | 1 | 14/16 PASS; DG-006 + DG-011 **pre-existing** (see below) | `/tmp/docgov.json` (session) |

`guards` manifest items: architecture-drift exit 0 (30.0 s), jooq-parity exit 0 (5.3 s), oidc-bundle
exit 0, instruction-governance exit 0, instruction-governance-selftest exit 0. Manifest identity:
commit `0cf42bdd`, tree `f5cfb38…`, no dirty paths.

## Artifact test arithmetic

| Metric | Before | After |
|---|---|---|
| tests | 170 | 170 |
| passed | 155 | 159 |
| failed | 15 | 11 |
| skipped | 0 | 0 |
| errors | 0 | 0 |

The 15 previous failures split as: 4 JDK-25 proxy failures (fixed test-side; `Code length 66819 is
outside the allowed range`) and 11 controlled-launch failures (`CONTROLLED_LAUNCH_REQUIRED`).
The 11 now have a supported entry (`verify l2`) but were not executed here because no
controller-produced PG18 binding exists in this environment. They are **not** claimed as passing.

## Pre-existing document-governance RED (not caused by this task)

`check-document-governance.sh` fails identically on the untouched base commit:

- DG-006 `PROTECTED_BASELINE_CHANGE`: `docs/architecture/governance/canonical-contracts/api-contract.md`
  semantic hash `6c7a217d…` vs pinned `f66f1019…`. The file is unchanged by this task
  (`git diff 6f995ce7 HEAD -- api-contract.md` is empty). Updating the protected pin requires explicit
  governance approval, so it was reported, not rewritten.
- DG-011 `INTRODUCED_BROKEN_LINK` (count 1) also appears with `--head 6f995ce7 --base 6f995ce7`, i.e.
  with an empty diff, so it cannot be attributed to these changes.

Evidence: `check-document-governance.sh --head 6f995ce7 --base 6f995ce7` → 14/16 PASS, decision FAIL.

## Blocked / not executed

| Item | Status | Reason |
|---|---|---|
| L2 controlled PostgreSQL18 selection | BLOCKED | no protected controller/binding; launcher fails closed by design |
| 11 `CONTROLLED_LAUNCH_REQUIRED` Artifact tests | BLOCKED | same as above |
| L3 real API/Worker/FFmpeg/browser E2E | NOT RUN | governance-only change; preflight reports `libx264` missing on host FFmpeg, `FFMPEG_WORKER_FONT` unset, PG18/Temporal/OIDC ports closed |
| `./gradlew test` full repository | NOT RUN | out of scope; module-level L1 used instead |

## How later agents should use this matrix

1. `python3 scripts/dev/verify preflight --level l1` before any work.
2. L1 for documentation, governance, test-only and local refactors.
3. L2 when SQL, jOOQ, persistence, Artifact, provider or application composition changes; supply the
   protected binding or stop and report the environment blocker.
4. L3 only for execution-path/runtime/media/deployment changes, and only when L3 preflight is PASS.
5. Do not replace a RED gate with a weaker one; retire a check only when its invariant no longer exists.
