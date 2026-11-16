# Modulith Contract Closure R1 — Test Matrix

**Task:** `MEDIA_PLATFORM_MODULITH_CONTRACT_CLOSURE_R1`
**Base:** `cf0bb5adb334ef3192c99b2d698e9faea763cb9f` / `ec5394422872affb60e8c1fa2bd8656ec04bdf8a`
**Candidate:** the commit that contains this matrix (see the delivery report)
**Evidence root:** `/home/user/Documents/workspace/projects/media-platform-modulith-contract-closure-r1-evidence/`

| # | Check | Command | Exit | Result |
|---|---|---|---|---|
| 1 | Base identity | `git rev-parse main`, `git ls-remote --heads origin main` | 0 | `cf0bb5ad…` / `ec539442…`, remote matches |
| 2 | Stale rules removed | 7 entries deleted from `ModularityTest` | — | 33 → 26; gate still PASS (0 unexpected) |
| 3 | Contract rules removed | 17 remaining contract entries deleted after declarations | — | 26 → 3 (graph only) |
| 4 | Modulith gate | `./gradlew :platform-app:test --tests '*ModularityTest' --no-daemon` | 0 | PASS, 1 test, 0 failures, **0 unexpected, 0 cycles, 3 rules** |
| 5 | Negative 1 — undeclared dependency | remove `artifact` → `typedschema :: records`, run the gate | 1 | FAIL as expected, 3 messages (`artifact` → `typedschema :: records`) |
| 6 | Negative 2 — direct adapter dependency | add `TimelineRevisionRepository` to `ProjectDashboardController`, run the gate | 1 | FAIL as expected (`web → timeline` on `TimelineRevisionRepository`) |
| 7 | Whole-build compile | `./gradlew compileJava compileTestJava --no-daemon` | 0 | PASS |
| 8 | Affected module tests | `./gradlew :operation-module:test :timeline-module:test :media-execution-plan-module:test :typed-schema-module:test :provider-plugin-runtime-module:test :bmf-provider-plugin-module:test` plus platform-app web/dashboard/assembly/Modulith | 1 | 1 274 tests run; 2 failures, **both pre-existing and unchanged from base** (see below) |
| 9 | D6 regression | `ProjectDashboardAuthorizationTest` (updated constructor) + the controller's endpoints | 0 | PASS (all dashboard/health/pending authorization tests) |
| 10 | Architecture drift | `bash scripts/check-architecture-drift.sh --quiet` | 0 | PASS (220 checks) |
| 11 | Guard suite | `python3 -B scripts/dev/verify guards` | 0 | PASS 7/7 |
| 12 | Document governance | `bash scripts/check-document-governance.sh --head HEAD --base main` | 0 | PASS 16/16 |
| 13 | Hygiene | `git diff --check`, `git status --porcelain` | 0 | clean |
| 14 | Graph kernel purity | no Spring/Modulith dependency added to `platform-algorithms/graph`; 3 rules unchanged | — | PASS |
| 15 | Provider/Bridge direction | host ↛ bmf, bmf ↛ host (unchanged) | — | PASS |

## Pre-existing failures (verified unchanged from the base commit)

| Test | Why it fails | Evidence |
|---|---|---|
| `timeline-module: Cfrhi1LegacyWriteAuthorityGuardTest.canonicalRestoreAuthorityIsActive` | The guard expects `TimelineRevisionController` to call `revisionSaveService.restoreRevision(...)` once, but direct restore was retired before this round (the controller returns "Direct revision restore retired; use a versioned Timeline Operation"). `git show HEAD:…TimelineRevisionController.java` contains 0 `restoreRevision` references. | `git diff HEAD --` the controller/test is empty |
| `timeline-module: RevisionCreatedEventTest.initializationError` | Extends `PostgresTestContainerSupport`; requires the controlled-launch PostgreSQL harness (`CONTROLLED_LAUNCH_REQUIRED`), same class of environment constraint as `WorkerFabricApplicationContextTest`. | initializer stack shows `PostgresTestContainerSupport.<clinit>` |

Neither failure is caused by this round; the affected contract surface (Modulith gate, operation
contract guard, platform-app web tests, provider suites) passes.

## Reused evidence

- The FFmpeg/BMF L3 runs from `MODULE_BOUNDARY_CONSOLIDATION_R1` remain valid: this round changes
  only Modulith declarations, one dead controller dependency and one test rule list. The production
  tree diff contains no RenderJob planning, Operation apply, Revision persistence, Provider binding,
  worker backend or artifact commit change. D6 is covered by the dashboard authorization test and
  the endpoint-level behavior is unchanged.
