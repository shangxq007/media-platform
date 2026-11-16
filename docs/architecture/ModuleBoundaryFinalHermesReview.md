# Module Boundary Final — Independent Review Record (Hermes)

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1_FINAL_CLOSURE`
**Reviewer:** Hermes — independent read-only reviewer agents attached to this session
**Producer:** DSH (the only writing agent)

> This file records **actual** independent reviews. Two read-only reviewer agents were launched
> before implementation (one broad, one bounded) and one reviewed the final diff. All were instructed
> not to write, not to run git write commands, and to mark unverified facts `UNVERIFIED`. Their
> verdicts are reproduced verbatim in substance; DSH did not author them.

## 1. Pre-implementation review A (broad, agent `4afc3db1-c483-475c-b134-03fa0f1ac2e4`)

Result: **APPROVE_WITH_CONDITIONS**

Verified facts:
- `ModularityTest` fails at BASE; the raw dump has 3519 messages, of which the test's 33 matching
  patterns leave **1004 unexpected**, and the debt register's "0 unexpected" claim is stale.
- The two largest groups (`execution -> render` 687, `operation -> timeline` 666) are **already
  allowlisted** and are not the failure driver.
- The only real cycle is intra-`platform-app`: `ArtifactCapabilityApplicationService` (config slice)
  calls `CapabilityExecutionDispatcher.workflowId` (runtime slice), and the worker boot class
  `@Import`s `config.PlatformClockConfiguration`.
- F-02's proposed move conflicts with `BmfArchitectureGuardTest` (EP05): bmf production must not
  contain `ProviderPlugin`/`org.pf4j`, bmf build must not contain the host module, the host build must
  contain the bmf dependency, and the exact source lists are pinned — an owner decision, not a
  mechanical move.

Conditions: (1) approve only the narrow cycle fix, not an F-01 green claim; (2) F-02 requires owner
authorisation for EP05 changes, test relocation and a real BMF regression; (3) keep
`createRuntimeBinding`'s executable validation.

## 2. Pre-implementation review B (bounded, agent `f985a5c3-df3d-449e-ab33-37978b1f3ef6`)

Result: **APPROVE_WITH_CONDITIONS**

Verified facts: the same config→runtime call edge and the pure static `workflowId`; runtime→config is
a Spring `@Import` (composition wiring); moving the scheme into
`com.example.platform.execution.admission` is the minimal cycle break; both callers already depend on
that contract; the `workflowId` bytes must stay identical or Temporal executions orphan; F-02's
mechanical steps are feasible but its registry/test surface must change.

## 3. Final review of the actual diff (agent `f985a5c3-df3d-449e-ab33-37978b1f3ef6`)

```text
HERMES_FINAL_REVIEW:

F01: APPROVE
F02: APPROVE (deferred, correct)
GRADLE_DEPENDENCIES: PASS
SPRING_ASSEMBLY: PASS
PF4J_RUNTIME: PASS
BMF_NATIVE_EVIDENCE: PASS (not applicable)
DIFF_SCOPE: PASS
RESULT: APPROVE_WITH_CONDITIONS
```

Reviewer's evidence points: the config→runtime call is gone and the dispatcher's static helper is
removed (no compatibility delegate); the new class returns byte-identical values and a test pins
them; no Gradle edge is added because both callers already depend on `media-execution-plan-module`
(`provider-plugin-runtime-module/build.gradle.kts:26 api(...)`); the diff is exactly two one-line
semantic edits plus two new files; the temporary dump test is absent; `RuntimeExecutionBackends.java`
is pinned by the P17 clean-forward ledger (P17-L-072), which confirms deferring F-02.

Reviewer's remaining condition: confirm that `ModularityTest`'s count is the **unexpected** violation
count (it is: the failure message is `Unexpected Modulith violations (messages=1002)`) and confirm the
residual `runtime -> config` `@Import` is not surfaced as a cycle (it is not; no cycle message).

## 4. DSH vs Hermes: who established what

| Claim | Established by |
|---|---|
| 3519 total / 1004 unexpected messages, 27 unexpected groups, 1 cycle | Hermes (independent recomputation) |
| `execution->render` and `operation->timeline` are allowlisted, not failures | Hermes (independent) |
| config→runtime call edge and runtime→config `@Import` | Hermes (file:line) |
| F-02 conflicts with EP05 `BmfArchitectureGuardTest` and P17-L-072 | Hermes (independent) + DSH verification of the guard source |
| Cycle removed, 1004 → 1002, golden test green | DSH (execution evidence) |
| Provider suite 111/0/0/3; Spring subset 47/0/2; guards 220 + 7/7 | DSH (execution evidence) |

## 5. Still unverified (independent of the above)

- F-01 green: not achieved; the declaration pass remains unimplemented.
- F-02: not implemented; BMF native regression not run this round.
- No reviewer ran Gradle (read-only constraint); build/test results are DSH-run evidence.
