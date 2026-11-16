# Modulith Contract Closure R1 — Closeout

**Task:** `MEDIA_PLATFORM_MODULITH_CONTRACT_CLOSURE_R1`
**Branch:** `task/modulith-contract-closure-r1`
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-modulith-contract-closure-r1`

```text
BASE_COMMIT: cf0bb5adb334ef3192c99b2d698e9faea763cb9f
BASE_TREE:   ec5394422872affb60e8c1fa2bd8656ec04bdf8a

RULES_BEFORE: 33
RULES_REMOVED_STALE: 7          (AV-008, AV-015, AV-022, AV-028, AV-031, AV-032, AV-033)
RULES_REPLACED_WITH_CONTRACT: 23 (AV-001..029 contractual items)
RULES_RETAINED: 3               (AV-011, AV-027, AV-030 — graph kernel, owner decision D4)
ALLOWED_VIOLATIONS_FINAL: 3

NAMED_INTERFACES_ADDED: 34 new package-info files + 23 extended package-info files + 17 operation
  source files carrying 21 type-level annotations
APPLICATION_MODULES_ADDED: new root package-info for execution, ingest, media, operation,
  persistence and timeline; existing roots extended with @ApplicationModule/allowedDependencies in
  artifact, workerfabric, outbox, delivery, federation, analytics, bmf, providerplugin, bmfprovider,
  fonttext, fontpipeline, audio, colorimage, commerce, entitlement, identity, render, workflow and
  the platform-app composition slices (see the declaration matrix)
ALLOWED_DEPENDENCIES_ADDED: per consumer module root, matching the measured edges only

TYPEDSCHEMA_RECORDS_CONTRACT: PACKAGE_LEVEL_NAMED_INTERFACE (D3-A), declared from the
  generator-immune src/main/modulith/java source root; generated records untouched
GRAPH_KERNEL_STATUS: PURE_JAVA (no Spring/Modulith dependency added; 3 precise rules kept)

AV019_REFACTOR: DONE — ProjectDashboardController no longer injects or imports
  timeline.adapter.TimelineRevisionRepository (it never called it); the adapter is not exposed
MODULITH_TEST: PASS (0 unexpected, 0 cycles, 3 rules)
NEGATIVE_BOUNDARY_TEST: PASS (two checks: undeclared dependency fails; direct
  web -> timeline.adapter import fails)

GRADLE_DEPENDENCY_GRAPH: PASS (no new cycles; provider host <-> BMF still absent)
AFFECTED_MODULE_TESTS: PASS except two pre-existing failures unrelated to this change
  (see the test matrix: a stale restore-guard expectation and a test that requires the
  controlled-launch PostgreSQL harness)
ARCHITECTURE_GUARDS: PASS (drift 220 checks; verify guards 7/7)
DOCUMENT_GOVERNANCE: PASS (16/16)

HERMES_REVIEW: recorded in ModulithContractClosureHermesReview.md

CANDIDATE_COMMIT: the commit that contains this report
CANDIDATE_TREE:   recorded in the delivery report

FINAL_RESULT: PASS
```

## What changed

**Rules.** The 7 stale entries (zero matches at the audit baseline) and the 23 contract entries
are gone; the three graph rules remain verbatim.

**Contracts.** Every legitimate cross-module dependency that the rules used to hide is now an
explicit Modulith contract: a root `@ApplicationModule` on the previously unmodeled modules, a
`@NamedInterface` on the consumed packages (or type-level annotations where the owning module's
guard requires type-level-only interfaces, e.g. `operation.plan` / `operation.operation`),
and matching `allowedDependencies` on the consumer. No adapter/persistence/internal package was
newly exposed (verified: every infrastructure/adapter `@NamedInterface` in the tree is pre-existing
and unchanged).

**D6.** `ProjectDashboardController` injected `TimelineRevisionRepository` but never called it.
The dead dependency (import, field, constructor parameter, assignment) is removed; the
controller behavior, endpoints, authorization and response shapes are unchanged.
`ProjectDashboardAuthorizationTest` was updated to the new constructor.

**Operation type-level contract.** A pre-existing guard
(`OperationInvocationContractTest`) requires the operation packages to declare their named
interfaces per type, not per package. The declaration pass therefore annotates the consumed
`operation.plan` / `operation.operation` types (including `OperationDefinition.V1/V2/V3` and
`TextOperationPlanner.TextPlanException`) instead of creating package-level interfaces; the
obsolete `operation/operation/package-info.java` is deleted. This also fixes that guard test,
which was red at the base commit.

**Debt register.** `docs/modulith-debt-register.md` now records the real state (3 rules, 7 removed,
23 replaced, 0 unexpected, 0 cycles, D6 resolved) and keeps the old 1 641/39 numbers explicitly
marked as historical.

## Verification

| Gate | Result |
|---|---|
| `:platform-app:test --tests '*ModularityTest'` | PASS (1 test, 0 failures, 3 rules) |
| Negative 1: remove `artifact` → `typedschema :: records` | FAIL as expected (3 messages) |
| Negative 2: re-add `web` → `timeline.adapter` | FAIL as expected (`web → timeline`) |
| `compileJava compileTestJava` | PASS |
| Affected modules (platform-app web/dashboard/assembly, operation, timeline, execution, typed-schema, provider host, bridge) | 1 274+ tests run; only two pre-existing failures (below) |
| `scripts/check-architecture-drift.sh --quiet` | PASS |
| `python3 -B scripts/dev/verify guards` | PASS 7/7 |
| `check-document-governance.sh` | PASS 16/16 |

Pre-existing failures (verified unchanged from the base commit):
- `Cfrhi1LegacyWriteAuthorityGuardTest.canonicalRestoreAuthorityIsActive` expects
  `TimelineRevisionController` to call `restoreRevision` once, but the controller's direct restore
  was retired before this round (`git show HEAD:…` contains 0 references). The test is stale.
- `RevisionCreatedEventTest` extends `PostgresTestContainerSupport` and requires the controlled
  launch harness (`CONTROLLED_LAUNCH_REQUIRED`), like the worker-fabric context test.

Neither is caused by this change and neither weakens the new contract surface; fixing the stale
guard is a separate owner decision (see the test matrix).
