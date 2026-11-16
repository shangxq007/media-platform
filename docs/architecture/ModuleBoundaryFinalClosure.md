# Module Boundary Final Closure — F-01 / F-02

> **SUPERSEDED (historical record).** This document records the state of the previous round (candidate `b58fc22d`). The owner-approved final implementation now delivers a green Modulith gate and the BMF bridge; see `ModuleBoundaryOwnerApprovedCloseout.md` and `ModuleBoundaryOwnerApprovedTestMatrix.md` for the current state.

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1_FINAL_CLOSURE`
**Branch:** `task/module-boundary-consolidation-r1` (existing task worktree)
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1`

```text
BASE_COMMIT: 4765eda5f0527c25d61139b2af4dbb5824db3efc
BASE_TREE:   636186a192c509fca6c326400b1b6bcb8386d3a1

PREVIOUS_CANDIDATE_COMMIT: ef80d09d07c73a4ae6b199b5b7bcd36945aecd83
PREVIOUS_CANDIDATE_TREE:   379d2c9c12d77687e145d9bea0e84b6c85f7402d

F01_ROOT_CAUSES: 3 independent causes behind 1004 unexpected violation messages
  1. The capability workflow-id scheme (a platform contract) was owned by the worker runtime slice
     (platform-app runtime.mediatask.CapabilityExecutionDispatcher.workflowId), but the authenticated
     API cancel path in the config slice called it, while the worker boot class @Imports
     config.PlatformClockConfiguration. Two composition-root slices formed the only dependency cycle.
  2. The cycle was the only genuine wrong-direction edge. The remaining unexpected messages are
     undeclared cross-module contracts (runtime->workerfabric 479, fontpipeline->fonttext 146,
     runtime->workflow 95, config->render 55, web->operation 48, ...) plus stale allowlist patterns
     whose message wording no longer matches (providerplugin groups).
  3. The largest raw groups (execution->render 687, operation->timeline 666) are already covered by
     ALLOWED_VIOLATIONS and were never the failure driver; message counts are not edit counts —
     they collapse into a few dozen distinct package/API declarations.

F01_IMPLEMENTATION: DONE (bounded)
  media-execution-plan-module com.example.platform.execution.admission.CapabilityExecutionWorkflowIds
  owns the scheme; both callers already depend on that contract. The dispatcher's static helper was
  removed (no compatibility delegate). A golden test pins the exact bytes.

F01_MODULARITY_TEST: FAIL (honest)
  Before: Cycle detected: Slice config -> Slice runtime -> Slice config; 1004 unexpected messages.
  After:  no cycle message; 1002 unexpected messages.
  The test remains RED on the pre-existing declaration debt (1002 messages / 27 distinct groups).
  ALLOWED_VIOLATIONS was not touched. F-01 is therefore PARTIAL, not PASS.

F02_ROOT_CAUSES: the generic provider host is coupled to one concrete provider
  provider-plugin-runtime-module/build.gradle.kts declares implementation(project(":bmf-provider-module"));
  the host module hosts providerplugin/bmf/BmfProviderExtension; RuntimeExecutionBackends.create()
  hardcodes new BmfExecutionBackend(); FfmpegWorkerPluginConfiguration and
  MediaTaskAdmissionEvidenceFactory branch on `instanceof BmfProviderExtension`.

F02_IMPLEMENTATION: NONE — deliberately blocked
  Moving the extension into bmf-provider-module directly contradicts the accepted EP05 guard
  bmf-provider-module/src/test/java/com/example/platform/bmf/BmfArchitectureGuardTest.java:
    * production must NOT contain "org.pf4j" / "ProviderPlugin" / "ProviderPluginContribution";
    * bmf-provider-module/build must NOT contain "provider-plugin-runtime-module";
    * provider-plugin-runtime-module/build MUST contain implementation(project(":bmf-provider-module"));
    * the exact BMF source/test file lists (including package-info.java) are pinned.
  The P17 clean-forward ledger (phase17-sandbox-isolation-clean-forward-ledger.tsv, P17-L-072) also
  pins RuntimeExecutionBackends.java as the active LocalProcessExecutionBackend path and requires an
  EP05 update before relocation. Changing either is an owner decision, not a mechanical move.

HOST_TO_BMF_DEPENDENCY: PRESENT (unchanged)

SPRING_ROLE_VERIFICATION: PASS
  API default role, WORKER @ConditionalOnProperty, COMBINED opt-in assembly, two worker binaries;
  no duplicate provider host/catalog beans. 47 tests / 0 failures / 2 skipped
  (CombinedRuntimeAssemblyTest, PlatformRuntimeRole*, FfmpegWorkerPluginConfigurationTest,
  ProviderRuntimeExecutionCompositionTest, MediaTaskAdmissionEvidenceFactoryTest).

PF4J_FFMPEG_VERIFICATION: PASS (unaffected)
  No change under provider-plugin-runtime-module, ffmpeg-provider-module, extension or plugin
  metadata. Provider suite 111 tests / 0 failures / 0 errors / 3 skipped. The FFmpeg plugin still
  loads through DefaultPluginManager with plugin.properties + @Extension; identity unchanged.

BMF_NATIVE_VERIFICATION: NOT_RUN (not required for this diff, still required for F-02)
  No BMF or provider-runtime code changed. The pinned runtime image
  localhost/media-platform-bmf-runtime:0.2.0-r1 exists locally and scripts/dev/prepare-bmf-runtime
  can stage a verified root, but no native BMF execution was run because F-02 is deferred; running it
  without the F-02 change would not validate anything new.

TIMELINE_TRIM_REGRESSION: PASS (reused, justified)
  No Timeline, Operation, RenderPlanning, frontend or provider-assembly code changed. The workflow-id
  bytes are identical, so capability-execution dispatch is unchanged. The accepted Trim L3 remains
  valid.

ARCHITECTURE_GUARDS: PASS (drift 220 checks; verify guards 7/7) — see the test matrix
DOCUMENT_GOVERNANCE: PASS (16/16)

HERMES_PRE_REVIEW: APPROVE_WITH_CONDITIONS (two independent read-only reviews, recorded in
  ModuleBoundaryFinalHermesReview.md; both confirmed the F-01 cycle facts and the F-02 EP05 conflict)
HERMES_FINAL_REVIEW: APPROVE_WITH_CONDITIONS (reviewed the actual diff: F01 APPROVE, F02 APPROVE as
  deferred, DIFF_SCOPE PASS, GRADLE_DEPENDENCIES PASS, SPRING_ASSEMBLY PASS, PF4J_RUNTIME PASS,
  BMF_NATIVE_EVIDENCE PASS as not-applicable)

OWNER_DECISIONS_REQUIRED:
  1. F-02 / EP05: choose (a) keep BMF pure and add a dedicated bridge module
     (bmf-provider-plugin-module) that owns the in-process contribution and depends on the host —
     preserves EP05's separation but still requires updating the EP05 guard's host-dependency and
     exact-file assertions; (b) relocate BmfProviderExtension into bmf-provider-module and formally
     reverse EP05's "BMF must not reference ProviderPlugin" rule; or (c) accept the coupling as
     designed and close F-02 as NO_CHANGE. Any choice needs a real BMF native regression.
  2. F-01 remainder: authorise the declaration pass for the 27 unexpected groups (named interfaces on
     ~29 consumed packages + allowedDependencies on ~15 modules + composition-root slices) or accept
     the red gate as recorded debt. This is a governance-scope decision, not a bug fix.

DEFERRED_FINDINGS: F-02, F-01 remainder, F-04 (unused-looking deps), F-05 (composite-resource-module),
  F-06 (storage-provider-opendal), F-07 (controllers in *.api packages) — unchanged from the previous
  closeout.

FINAL_CANDIDATE_COMMIT: the commit that contains this report
FINAL_CANDIDATE_TREE:   recorded in the delivery report (parent CODE_COMMIT baa60c15)

FINAL_RESULT: PARTIAL
```

## Why PARTIAL and not PASS

PASS requires a green `ModularityTest`, a decoupled provider host with a real BMF native regression,
and an independent review approval. This round delivered: the only real dependency cycle removed and
independently reviewed, the F-02 governance conflict identified precisely (two accepted guards), and
the entire verification set green. It did not deliver a green Modulith gate or F-02, because both
require an Owner decision (governance scope / EP05 reversal), and the task forbids widening the
allowlist or touching accepted guards without that decision.

## What changed (exactly)

| File | Change |
|---|---|
| `media-execution-plan-module/.../execution/admission/CapabilityExecutionWorkflowIds.java` | new: the shared workflow-id scheme |
| `media-execution-plan-module/.../execution/admission/CapabilityExecutionWorkflowIdsTest.java` | new: golden-byte test |
| `platform-app/.../config/mediaexecution/ArtifactCapabilityApplicationService.java` | one call site uses the contract class |
| `platform-app/.../runtime/mediatask/CapabilityExecutionDispatcher.java` | static helper removed; dispatch uses the contract class |

No build file, no Spring configuration, no provider SPI, no domain contract, no database, no
frontend, no deployment change. The temporary `ModulithViolationDumpTest` used to aggregate the
violations was never committed.
